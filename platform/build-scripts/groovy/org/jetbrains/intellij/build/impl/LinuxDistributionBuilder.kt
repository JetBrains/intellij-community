// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl;

import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.Strings;
import groovy.lang.Closure;
import groovy.lang.GString;
import groovy.lang.Reference;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import kotlin.Pair;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.build.*;
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoGeneratorKt;
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoLaunchData;
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoValidatorKt;
import org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder;
import org.jetbrains.intellij.build.io.FileKt;
import org.jetbrains.intellij.build.io.ProcessKt;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class LinuxDistributionBuilder extends OsSpecificDistributionBuilder {
  public LinuxDistributionBuilder(BuildContext context, LinuxDistributionCustomizer customizer, Path ideaProperties) {
    this.context = context;
    this.customizer = customizer;
    this.ideaProperties = ideaProperties;

    final String s = context.getApplicationInfo().isEAP() ? customizer.getIconPngPathForEAP() : null;
    String iconPng = StringGroovyMethods.asBoolean(s) ? s : customizer.getIconPngPath();
    iconPngPath = iconPng == null || iconPng.isEmpty() ? null : Path.of(iconPng);
  }

  @Override
  public OsFamily getTargetOs() {
    return OsFamily.LINUX;
  }

  @Override
  public void copyFilesForOsDistribution(@NotNull final Path unixDistPath, final JvmArchitecture arch) {
    BuildHelperKt.span(
      TraceManager.spanBuilder("copy files for os distribution").setAttribute("os", getTargetOs().osName).setAttribute("arch", arch.name()),
      new Runnable() {
        @Override
        public void run() {
          Path distBinDir = unixDistPath.resolve("bin");

          FileKt.copyDir(context.getPaths().getCommunityHomeDir().resolve("bin/linux"), distBinDir);
          DistUtilKt.unpackPty4jNative(context, unixDistPath, "linux");
          DistUtilKt.generateBuildTxt(context, unixDistPath);
          DistUtilKt.copyDistFiles(context, unixDistPath);
          List<String> extraJarNames = DistUtilKt.addDbusJava(context, unixDistPath.resolve("lib"));
          Files.copy(ideaProperties, distBinDir.resolve(ideaProperties.getFileName()), StandardCopyOption.REPLACE_EXISTING);
          //todo[nik] converting line separators to unix-style make sense only when building Linux distributions under Windows on a local machine;
          // for real installers we need to checkout all text files with 'lf' separators anyway
          BuildUtils.convertLineSeparators(unixDistPath.resolve("bin/idea.properties"), "\n");
          if (iconPngPath != null) {
            Files.copy(iconPngPath, distBinDir.resolve(context.getProductProperties().getBaseFileName() + ".png"),
                       StandardCopyOption.REPLACE_EXISTING);
          }

          generateVMOptions(distBinDir);
          UnixScriptBuilder.generateScripts(context, extraJarNames, distBinDir, OsFamily.LINUX);
          generateReadme(unixDistPath);
          generateVersionMarker(unixDistPath, context);
          RepairUtilityBuilder.bundle(context, OsFamily.LINUX, arch, unixDistPath);
          customizer.copyAdditionalFiles(context, unixDistPath, arch);
        }
      });
  }

  @Override
  public void buildArtifacts(@NotNull final Path osAndArchSpecificDistPath, @NotNull final JvmArchitecture arch) {
    copyFilesForOsDistribution(osAndArchSpecificDistPath, arch);
    context.executeStep(TraceManager.spanBuilder("build linux .tar.gz").setAttribute("arch", arch.name()),
                        BuildOptions.LINUX_ARTIFACTS_STEP, new Closure<Void>(this, this) {
        public void doCall(Object it) {
          if (customizer.getBuildTarGzWithoutBundledRuntime()) {
            context.executeStep("Build Linux .tar.gz without bundled JRE", BuildOptions.LINUX_TAR_GZ_WITHOUT_BUNDLED_JRE_STEP,
                                new Closure<Path>(LinuxDistributionBuilder.this, LinuxDistributionBuilder.this) {
                                  public Path doCall(Object it) {
                                    return buildTarGz(null, osAndArchSpecificDistPath, NO_JBR_SUFFIX);
                                  }

                                  public Path doCall() {
                                    return doCall(null);
                                  }
                                });
          }


          if (customizer.getBuildOnlyBareTarGz()) {
            return;
          }


          Path jreDirectoryPath = context.getBundledRuntime().extract(BundledRuntimeImpl.getProductPrefix(context), OsFamily.LINUX, arch);
          Path tarGzPath = buildTarGz(jreDirectoryPath.toString(), osAndArchSpecificDistPath, "");
          context.getBundledRuntime().checkExecutablePermissions(tarGzPath, getRootDirectoryName(), OsFamily.LINUX);

          if (jreDirectoryPath != null) {
            buildSnapPackage(jreDirectoryPath.toString(), osAndArchSpecificDistPath);
          }
          else {
            context.getMessages().info("Skipping building Snap packages because no modular JRE are available");
          }

          Path tempTar = Files.createTempDirectory(context.getPaths().getTempDir(), "tar-");
          try {
            ArchiveUtils.unTar(tarGzPath, tempTar);
            String tarRoot = customizer.getRootDirectoryName(context.getApplicationInfo(), context.getBuildNumber());
            RepairUtilityBuilder.generateManifest(context, tempTar.resolve(tarRoot), tarGzPath.getFileName().toString());
          }
          finally {
            NioFiles.deleteRecursively(tempTar);
          }
        }

        public void doCall() {
          doCall(null);
        }
      });
  }

  private void generateVMOptions(Path distBinDir) {
    String fileName = (String)context.getProductProperties().getBaseFileName() + "64.vmoptions";
    List<String> vmOptions =
      DefaultGroovyMethods.plus(VmOptionsGenerator.computeVmOptions(context.getApplicationInfo().isEAP(), context.getProductProperties()),
                                new ArrayList<String>(Arrays.asList("-Dsun.tools.attach.tmp.only=true")));
    Files.writeString(distBinDir.resolve(fileName), String.join("\n", vmOptions) + "\n", StandardCharsets.US_ASCII);
  }

  private void generateReadme(Path unixDistPath) {
    String fullName = context.getApplicationInfo().getProductName();

    Path sourceFile = context.getPaths().getCommunityHomeDir().resolve("platform/build-scripts/resources/linux/Install-Linux-tar.txt");
    BuildUtils.assertUnixLineEndings(sourceFile);

    Path targetFile = unixDistPath.resolve("Install-Linux-tar.txt");

    FileKt.substituteTemplatePlaceholders(sourceFile, targetFile, "@@", new ArrayList<Pair<String, String>>(
      Arrays.asList(new Pair<String, String>("product_full", fullName),
                    new Pair<String, String>("product", context.getProductProperties().getBaseFileName()),
                    new Pair<String, String>("product_vendor", context.getApplicationInfo().getShortCompanyName()),
                    new Pair<String, String>("system_selector", context.getSystemSelector()))));
  }

  private static void generateVersionMarker(Path unixDistPath, final BuildContext context) {
    Path targetDir = unixDistPath.resolve("lib");
    Files.createDirectories(targetDir);
    Files.writeString(targetDir.resolve("build-marker-" + context.getFullBuildNumber()), context.getFullBuildNumber());
  }

  @Override
  public List<String> generateExecutableFilesPatterns(boolean includeJre) {
    List<String> patterns =
      DefaultGroovyMethods.plus(new ArrayList<String>(Arrays.asList("bin/*.sh", "bin/*.py", "bin/fsnotifier*", "bin/remote-dev-server.sh")),
                                customizer.getExtraExecutables());
    if (includeJre) {
      patterns = DefaultGroovyMethods.plus(patterns, context.getBundledRuntime().executableFilesPatterns(OsFamily.LINUX));
    }

    return ((List<String>)(patterns));
  }

  @Override
  public List<String> getArtifactNames(final BuildContext context) {
    List<String> suffixes = new ArrayList<String>();
    if (customizer.getBuildTarGzWithoutBundledRuntime()) {
      suffixes = DefaultGroovyMethods.plus(suffixes, NO_JBR_SUFFIX);
    }

    if (!customizer.getBuildOnlyBareTarGz()) {
      suffixes = DefaultGroovyMethods.plus(suffixes, "");
    }

    return DefaultGroovyMethods.collect(suffixes, new Closure<String>(this, this) {
      public String doCall(String it) { return artifactName(context, it); }

      public String doCall() {
        return doCall(null);
      }
    });
  }

  private static String artifactName(BuildContext buildContext, final String suffix) {
    final String baseName =
      buildContext.getProductProperties().getBaseArtifactName(buildContext.getApplicationInfo(), buildContext.getBuildNumber());
    return baseName + suffix + ".tar.gz";
  }

  private String getRootDirectoryName() {
    return customizer.getRootDirectoryName(context.getApplicationInfo(), context.getBuildNumber());
  }

  private Path buildTarGz(@Nullable final String jreDirectoryPath, Path unixDistPath, String suffix) {
    final String tarRoot = getRootDirectoryName();
    String tarName = artifactName(context, suffix);
    final Path tarPath = context.getPaths().getArtifactDir().resolve(tarName);
    final Reference<List<String>> paths =
      new Reference<List<String>>(new ArrayList<String>(Arrays.asList(context.getPaths().getDistAll(), unixDistPath.toString())));

    String javaExecutablePath = null;
    if (jreDirectoryPath != null) {
      paths.set(DefaultGroovyMethods.plus(paths.get(), jreDirectoryPath));
      javaExecutablePath = "jbr/bin/java";
      if (!Files.exists(Path.of(jreDirectoryPath, javaExecutablePath))) {
        throw new IllegalStateException(javaExecutablePath + " was not found under " + jreDirectoryPath);
      }
    }


    String productJsonDir = new File(context.getPaths().getTemp(), "linux.dist.product-info.json" + suffix).getAbsolutePath();
    generateProductJson(Paths.get(productJsonDir), javaExecutablePath);
    paths.set(DefaultGroovyMethods.plus(paths.get(), productJsonDir));

    final List<String> executableFilesPatterns = generateExecutableFilesPatterns(jreDirectoryPath != null);
    final GString description = "archive" + jreDirectoryPath != null ? "" : " (without JRE)";

    return context.getMessages().block("Build Linux tar.gz " + String.valueOf(description), new Closure<Path>(this, this) {
      public Path doCall(Object it) {
        context.getMessages().progress("Building Linux tar.gz " + String.valueOf(description));
        DefaultGroovyMethods.each(paths.get(), new Closure<Void>(LinuxDistributionBuilder.this, LinuxDistributionBuilder.this) {
          public void doCall(String it) {
            BuildTasksImplKt.updateExecutablePermissions(Path.of(it), executableFilesPatterns);
          }

          public void doCall() {
            doCall(null);
          }
        });
        ArchiveUtils.tar(tarPath, tarRoot, paths.get(), context.getOptions().getBuildDateInSeconds());
        ProductInfoValidatorKt.checkInArchive(context, tarPath, tarRoot);
        context.notifyArtifactBuilt(tarPath);
        return tarPath;
      }

      public Path doCall() {
        return doCall(null);
      }
    });
  }

  private String generateProductJson(@NotNull Path targetDir, String javaExecutablePath) {
    final String scriptName = context.getProductProperties().getBaseFileName();

    Path file = targetDir.resolve(ProductInfoGeneratorKt.PRODUCT_INFO_FILE_NAME);
    Files.createDirectories(targetDir);

    String json = ProductInfoGeneratorKt.generateMultiPlatformProductJson("bin", context.getBuiltinModule(), List.of(
      new ProductInfoLaunchData(OsFamily.LINUX.osName, "bin/" + scriptName + ".sh", javaExecutablePath,
                                "bin/" + scriptName + "64.vmoptions", BuildTasksImplKt.getLinuxFrameClass(context))), context);
    Files.writeString(file, json);
    return ((String)(json));
  }

  private void buildSnapPackage(final String jreDirectoryPath, final Path unixDistPath) {
    if (!context.getOptions().getBuildUnixSnaps() || customizer.getSnapName() == null) {
      return;
    }


    if (iconPngPath == null) {
      context.getMessages().error("'iconPngPath' not set");
    }

    if (Strings.isEmpty(customizer.getSnapDescription())) {
      context.getMessages().error("'snapDescription' not set");
    }


    final Path snapDir = context.getPaths().getBuildOutputDir().resolve("dist.snap");

    context.getMessages().block("build Linux .snap package", new Closure<Void>(this, this) {
      public void doCall(Object it) {
        context.getMessages().progress("Preparing files");

        final Path unixSnapDistPath = context.getPaths().getBuildOutputDir().resolve("dist.unix.snap");
        FileKt.copyDir(unixDistPath, unixSnapDistPath);

        String productName = context.getApplicationInfo().getProductNameWithEdition();

        FileKt.substituteTemplatePlaceholders(
          context.getPaths().getCommunityHomeDir().resolve("platform/platform-resources/src/entry.desktop"),
          snapDir.resolve(customizer.getSnapName() + ".desktop"), "\$", new ArrayList<Pair<String, String>>(
            Arrays.asList(new Pair<String, String>("NAME", productName), new Pair<String, String>("ICON", "${SNAP}/bin/" +
                                                                                                          context.getProductProperties()
                                                                                                            .getBaseFileName() +
                                                                                                          ".png".toString()),
                          new Pair<String, String>("SCRIPT", customizer.getSnapName()),
                          new Pair<String, String>("COMMENT", context.getApplicationInfo().getMotto()),
                          new Pair<String, String>("WM_CLASS", BuildTasksImplKt.getLinuxFrameClass(context)))));

        FileKt.moveFile(iconPngPath, snapDir.resolve(customizer.getSnapName() + ".png"));

        Path snapcraftTemplate =
          context.getPaths().getCommunityHomeDir().resolve("platform/build-scripts/resources/linux/snap/snapcraft-template.yaml");
        final String replace = context.getApplicationInfo().getVersionSuffix().replace(" ", "-");
        final String versionSuffix = StringGroovyMethods.asBoolean(replace) ? replace : "";
        final String version = (String)StringGroovyMethods.asBoolean(
          context.getApplicationInfo().getMajorVersion() + "." + context.getApplicationInfo().getMinorVersion() + versionSuffix.isEmpty())
                               ? ""
                               : "-" + versionSuffix;

        FileKt.substituteTemplatePlaceholders(snapcraftTemplate, snapDir.resolve("snapcraft.yaml"), "\$",
                                              new ArrayList<Pair<String, String>>(
                                                Arrays.asList(new Pair<String, String>("NAME", customizer.getSnapName()),
                                                              new Pair<String, String>("VERSION", version),
                                                              new Pair<String, String>("SUMMARY", productName),
                                                              new Pair<String, String>("DESCRIPTION", customizer.getSnapDescription()),
                                                              new Pair<String, String>("GRADE", context.getApplicationInfo().isEAP()
                                                                                                ? "devel"
                                                                                                : "stable"),
                                                              new Pair<String, String>("SCRIPT", "bin/" +
                                                                                                 context.getProductProperties()
                                                                                                   .getBaseFileName() +
                                                                                                 ".sh".toString()))));

        DefaultGroovyMethods.each(
          new FileSet(unixSnapDistPath).include("bin/*.sh").include("bin/*.py").include("bin/fsnotifier*").enumerate(),
          new Closure<Void>(LinuxDistributionBuilder.this, LinuxDistributionBuilder.this) {
            public void doCall(Path it) { makeFileExecutable(it); }

            public void doCall() {
              doCall(null);
            }
          });

        DefaultGroovyMethods.each(new FileSet(Path.of(jreDirectoryPath)).include("jbr/bin/*").enumerate(),
                                  new Closure<Void>(LinuxDistributionBuilder.this, LinuxDistributionBuilder.this) {
                                    public void doCall(Path it) { makeFileExecutable(it); }

                                    public void doCall() {
                                      doCall(null);
                                    }
                                  });

        if (!customizer.getExtraExecutables().isEmpty()) {
          for (Path distPath : new ArrayList<Path>(Arrays.asList(unixSnapDistPath, context.getPaths().getDistAllDir()))) {
            DefaultGroovyMethods.each(DefaultGroovyMethods.tap(new FileSet(distPath),
                                                               new Closure<List<String>>(LinuxDistributionBuilder.this,
                                                                                         LinuxDistributionBuilder.this) {
                                                                 public List<String> doCall(FileSet it) {
                                                                   return DefaultGroovyMethods.each(customizer.getExtraExecutables(),
                                                                                                    new Closure<FileSet>(
                                                                                                      LinuxDistributionBuilder.this,
                                                                                                      LinuxDistributionBuilder.this) {
                                                                                                      public FileSet doCall(String it) {
                                                                                                        return include(it);
                                                                                                      }

                                                                                                      public FileSet doCall() {
                                                                                                        return doCall(null);
                                                                                                      }
                                                                                                    });
                                                                 }

                                                                 public List<String> doCall() {
                                                                   return doCall(null);
                                                                 }
                                                               }).enumerateNoAssertUnusedPatterns(),
                                      new Closure<Void>(LinuxDistributionBuilder.this, LinuxDistributionBuilder.this) {
                                        public void doCall(Path it) { makeFileExecutable(it); }

                                        public void doCall() {
                                          doCall(null);
                                        }
                                      });
          }
        }


        ProductInfoValidatorKt.validateProductJson(generateProductJson(unixSnapDistPath, "jbr/bin/java"), "",
                                                   List.of(context.getPaths().getDistAllDir(), unixSnapDistPath, Path.of(jreDirectoryPath)),
                                                   List.of(), context);

        Path resultDir = snapDir.resolve("result");
        Files.createDirectories(resultDir);
        context.getMessages().progress("Building package");

        String snapArtifact = customizer.getSnapName() + "_" + String.valueOf(version) + "_amd64.snap".toString();
        ProcessKt.runProcess(new ArrayList<String>(Arrays.asList("docker", "run", "--rm", "--volume=" +
                                                                                          String.valueOf(snapDir) +
                                                                                          "/snapcraft.yaml:/build/snapcraft.yaml:ro".toString(),
                                                                 "--volume=" +
                                                                 String.valueOf(snapDir) +
                                                                 "/" +
                                                                 customizer.getSnapName() +
                                                                 ".desktop:/build/snap/gui/" +
                                                                 customizer.getSnapName() +
                                                                 ".desktop:ro".toString(), "--volume=" +
                                                                                           String.valueOf(snapDir) +
                                                                                           "/" +
                                                                                           customizer.getSnapName() +
                                                                                           ".png:/build/prime/meta/gui/icon.png:ro".toString(),
                                                                 "--volume=" + String.valueOf(snapDir) + "/result:/build/result".toString(),
                                                                 "--volume=" +
                                                                 context.getPaths().getDistAll() +
                                                                 ":/build/dist.all:ro".toString(), "--volume=" +
                                                                                                   String.valueOf(unixSnapDistPath) +
                                                                                                   ":/build/dist.unix:ro".toString(),
                                                                 "--volume=" + jreDirectoryPath + ":/build/jre:ro".toString(),
                                                                 "--workdir=/build", context.getOptions().getSnapDockerImage(), "snapcraft",
                                                                 "snap", "-o", "result/" + snapArtifact.toString())), snapDir,
                             context.getMessages());

        FileKt.moveFileToDir(resultDir.resolve(snapArtifact), context.getPaths().getArtifactDir());
        context.notifyArtifactWasBuilt(context.getPaths().getArtifactDir().resolve(snapArtifact));
      }

      public void doCall() {
        doCall(null);
      }
    });
  }

  private static void makeFileExecutable(Path file) {
    Span.current().addEvent("set file permission to 0755", Attributes.of(AttributeKey.stringKey("file"), file.toString()));
    //noinspection SpellCheckingInspection
    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
  }

  public static void copyFile(Path source, Path target, CopyOption... options) {
    Path parent = target.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }


    List<CopyOption> optionsList = DefaultGroovyMethods.toList(options);

    if (Files.isSymbolicLink(source)) {
      // Append 'NOFOLLOW_LINKS' copy option to be able to copy symbolic links.
      if (!optionsList.contains(LinkOption.NOFOLLOW_LINKS)) {
        optionsList.add(LinkOption.NOFOLLOW_LINKS);
      }
    }


    CopyOption[] copyOptions = optionsList.toArray(new CopyOption[optionsList.size()]);
    Files.copy(source, target, copyOptions);
  }

  private static final String NO_JBR_SUFFIX = "-no-jbr";
  private final LinuxDistributionCustomizer customizer;
  private final Path ideaProperties;
  private final Path iconPngPath;
  private final BuildContext context;
}
