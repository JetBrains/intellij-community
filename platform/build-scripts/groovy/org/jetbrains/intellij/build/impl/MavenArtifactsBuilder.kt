// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.NameUtilCore;
import groovy.lang.Closure;
import groovy.lang.Reference;
import groovy.util.AntBuilder;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.ResourceGroovyMethods;
import org.jetbrains.intellij.build.*;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;
import org.jetbrains.jps.model.library.JpsRepositoryLibraryType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.module.*;

import java.io.BufferedWriter;
import java.io.File;
import java.util.*;
import java.util.function.BiPredicate;

/**
 * Generates Maven artifacts for IDE and plugin modules. Artifacts aren't generated for modules which depends on non-repository libraries.
 *
 * @see ProductProperties#mavenArtifacts
 * @see BuildOptions#MAVEN_ARTIFACTS_STEP
 */
public class MavenArtifactsBuilder {
  public MavenArtifactsBuilder(BuildContext buildContext, boolean skipNothing) {
    this.buildContext = buildContext;
    this.skipNothing = skipNothing;
  }

  public MavenArtifactsBuilder(BuildContext buildContext) {
    this(buildContext, false);
  }

  public void generateMavenArtifacts(List<String> namesOfModulesToPublish,
                                     List<String> namesOfModulesToSquashAndPublish,
                                     String outputDir) {
    final Map<MavenArtifactData, List<JpsModule>> modulesToPublish = new HashMap<MavenArtifactData, List<JpsModule>>();

    Map<JpsModule, MavenArtifactData> regularModulesToPublish = generateMavenArtifactData(namesOfModulesToPublish);
    regularModulesToPublish.forEach(new Closure<List<JpsModule>>(this, this) {
      public List<JpsModule> doCall(Object aModule, Object artifactData) {
        return putAt0(modulesToPublish, artifactData, Collections.singletonList((JpsModule)aModule));
      }
    });

    final Map<JpsModule, MavenArtifactData> squashingMavenArtifactsData = generateMavenArtifactData(namesOfModulesToSquashAndPublish);
    namesOfModulesToSquashAndPublish.forEach(new Closure<List<JpsModule>>(this, this) {
      public List<JpsModule> doCall(Object moduleName) {
        JpsModule module = buildContext.findRequiredModule((String)moduleName);
        Set<JpsModule> modules =
          JpsJavaExtensionService.dependencies(module).recursively().withoutSdk().includedIn(JpsJavaClasspathKind.runtime(false))
            .getModules();

        final Set<MavenCoordinates> moduleCoordinates = DefaultGroovyMethods.toSet(
          DefaultGroovyMethods.collect(modules, new Closure<MavenCoordinates>(MavenArtifactsBuilder.this, MavenArtifactsBuilder.this) {
            public MavenCoordinates doCall(Object aModule) { return generateMavenCoordinatesForModule((JpsModule)aModule); }
          }));
        List<MavenArtifactDependency> dependencies = DefaultGroovyMethods.findAll(DefaultGroovyMethods.unique(
          DefaultGroovyMethods.collectMany(modules, new Closure<List<MavenArtifactDependency>>(MavenArtifactsBuilder.this,
                                                                                               MavenArtifactsBuilder.this) {
            public List<MavenArtifactDependency> doCall(Object aModule) {
              return squashingMavenArtifactsData.get(aModule).getDependencies();
            }
          })), new Closure(MavenArtifactsBuilder.this, MavenArtifactsBuilder.this) {
          public Object doCall(Object dependency) {
            return !moduleCoordinates.contains(((MavenArtifactDependency)dependency).getCoordinates());
          }
        });

        MavenCoordinates coordinates = generateMavenCoordinatesForSquashedModule(module);
        return putAt0(modulesToPublish, new MavenArtifactData(coordinates, dependencies), DefaultGroovyMethods.toList(modules));
      }
    });

    buildContext.getMessages().progress("Generating Maven artifacts for " + String.valueOf(modulesToPublish.size()) + " modules");
    buildContext.getMessages().debug("Generate artifacts for the following modules:");
    DefaultGroovyMethods.each(modulesToPublish, new Closure<Void>(this, this) {
      public void doCall(Object data, final Object modules) {
        buildContext.getMessages().debug("  [" +
                                         DefaultGroovyMethods.join(DefaultGroovyMethods.collect((Iterable<JpsModule>)modules,
                                                                                                new Closure<String>(
                                                                                                  DUMMY__1234567890_DUMMYYYYYY___.this,
                                                                                                  DUMMY__1234567890_DUMMYYYYYY___.this) {
                                                                                                  public String doCall(JpsModule it) { return it.getName(); }

                                                                                                  public String doCall() {
                                                                                                    return doCall(null);
                                                                                                  }
                                                                                                }), ",") +
                                         "] -> " +
                                         String.valueOf(((MavenArtifactData)data).getCoordinates()));
      }
    });
    layoutMavenArtifacts(modulesToPublish, outputDir);
  }

  @SuppressWarnings("GrUnresolvedAccess")
  private void layoutMavenArtifacts(final Map<MavenArtifactData, List<JpsModule>> modulesToPublish, String outputDir) {
    final BiPredicate<JpsModule, BuildContext> publishSourcesFilter =
      buildContext.getProductProperties().getMavenArtifacts().getPublishSourcesFilter();
    final BuildContext buildContext = this.buildContext;
    final Map<MavenArtifactData, String> pomXmlFiles = new LinkedHashMap<MavenArtifactData, String>();
    DefaultGroovyMethods.each(modulesToPublish, new Closure(this, this) {
      public void doCall(final Object artifactData, Object modules) {
        String filePath = (String)buildContext.getPaths().getTemp() +
                          "/pom-files/" +
                          ((MavenArtifactData)artifactData).getCoordinates().getDirectoryPath() +
                          "/" +
                          ((MavenArtifactData)artifactData).getCoordinates().getFileName("", "pom");
        pomXmlFiles.put((MavenArtifactData)artifactData, filePath);
        generatePomXmlFile(filePath, (MavenArtifactData)artifactData);
      }
    });
    final AntBuilder ant = LayoutBuilder.getAnt();
    new LayoutBuilder(buildContext).layout(buildContext.getPaths().getArtifacts() + "/" + outputDir,
                                           new Closure<Map<MavenArtifactData, List<JpsModule>>>(this, this) {
                                             public Map<MavenArtifactData, List<JpsModule>> doCall(Object it) {
                                               return DefaultGroovyMethods.each(modulesToPublish, new Closure(MavenArtifactsBuilder.this,
                                                                                                              MavenArtifactsBuilder.this) {
                                                 public void doCall(final Object artifactData, final Object modules) {
                                                   dir(((MavenArtifactData)artifactData).getCoordinates().getDirectoryPath(),
                                                       new Closure(MavenArtifactsBuilder.this, MavenArtifactsBuilder.this) {
                                                         public void doCall(Object it) {
                                                           LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(1);
                                                           map.put("file", pomXmlFiles.get(artifactData));
                                                           ant.invokeMethod("fileset", new Object[]{map});
                                                           final List<JpsModule> modulesWithSources =
                                                             DefaultGroovyMethods.findAll((List<JpsModule>)modules,
                                                                                          new Closure<Boolean>(MavenArtifactsBuilder.this,
                                                                                                               MavenArtifactsBuilder.this) {
                                                                                            public Boolean doCall(Object aModule) {
                                                                                              return !DefaultGroovyMethods.isEmpty(
                                                                                                ((JpsModule)aModule).getSourceRoots(
                                                                                                  JavaSourceRootType.SOURCE)) ||
                                                                                                     !DefaultGroovyMethods.isEmpty(
                                                                                                       ((JpsModule)aModule).getSourceRoots(
                                                                                                         JavaResourceRootType.RESOURCE));
                                                                                            }
                                                                                          });

                                                           LinkedHashMap<String, String> map1 = new LinkedHashMap<String, String>(4);
                                                           map1.put("name", ((MavenArtifactData)artifactData).getCoordinates()
                                                             .getFileName("", "jar"));
                                                           map1.put("duplicate", "fail");
                                                           map1.put("filesetmanifest", "merge");
                                                           map1.put("whenmanifestonly", "create");
                                                           ant.invokeMethod("jar", new Object[]{map1,
                                                             new Closure(DUMMY__1234567890_DUMMYYYYYY___.this,
                                                                         DUMMY__1234567890_DUMMYYYYYY___.this) {
                                                               public void doCall(Object it) {
                                                                 modulesWithSources.forEach(
                                                                   new Closure(DUMMY__1234567890_DUMMYYYYYY___.this,
                                                                               DUMMY__1234567890_DUMMYYYYYY___.this) {
                                                                     public void doCall(Object aModule) {
                                                                       module(((JpsModule)aModule).getName());
                                                                     }
                                                                   });
                                                               }

                                                               public void doCall() {
                                                                 doCall(null);
                                                               }
                                                             }});

                                                           final List<JpsModule> publishSourcesForModules =
                                                             DefaultGroovyMethods.findAll((List<JpsModule>)modules,
                                                                                          new Closure<Boolean>(MavenArtifactsBuilder.this,
                                                                                                               MavenArtifactsBuilder.this) {
                                                                                            public Boolean doCall(Object aModule) {
                                                                                              return publishSourcesFilter.test(
                                                                                                (JpsModule)aModule, buildContext);
                                                                                            }
                                                                                          });
                                                           if (!publishSourcesForModules.isEmpty() && !modulesWithSources.isEmpty()) {
                                                             zip(((MavenArtifactData)artifactData).getCoordinates()
                                                                   .getFileName("sources", "jar"),
                                                                 new Closure(MavenArtifactsBuilder.this, MavenArtifactsBuilder.this) {
                                                                   public void doCall(Object it) {
                                                                     publishSourcesForModules.forEach(
                                                                       new Closure<Iterable<JpsTypedModuleSourceRoot<JavaResourceRootProperties>>>(
                                                                         MavenArtifactsBuilder.this, MavenArtifactsBuilder.this) {
                                                                         public Iterable<JpsTypedModuleSourceRoot<JavaResourceRootProperties>> doCall(
                                                                           Object aModule) {
                                                                           DefaultGroovyMethods.each(
                                                                             ((JpsModule)aModule).getSourceRoots(JavaSourceRootType.SOURCE),
                                                                             new Closure(MavenArtifactsBuilder.this,
                                                                                         MavenArtifactsBuilder.this) {
                                                                               public Object doCall(Object root) {
                                                                                 LinkedHashMap<String, String> map2 =
                                                                                   new LinkedHashMap<String, String>(2);
                                                                                 map2.put("dir",
                                                                                          ((JpsTypedModuleSourceRoot<JavaSourceRootProperties>)root).getFile()
                                                                                            .getAbsolutePath());
                                                                                 map2.put("prefix",
                                                                                          ((JpsTypedModuleSourceRoot<JavaSourceRootProperties>)root).getProperties()
                                                                                            .getPackagePrefix().replace(".", "/"));
                                                                                 return ant.invokeMethod("zipfileset", new Object[]{map2});
                                                                               }
                                                                             });
                                                                           return DefaultGroovyMethods.each(
                                                                             ((JpsModule)aModule).getSourceRoots(
                                                                               JavaResourceRootType.RESOURCE),
                                                                             new Closure(MavenArtifactsBuilder.this,
                                                                                         MavenArtifactsBuilder.this) {
                                                                               public Object doCall(Object root) {
                                                                                 LinkedHashMap<String, String> map2 =
                                                                                   new LinkedHashMap<String, String>(2);
                                                                                 map2.put("dir",
                                                                                          ((JpsTypedModuleSourceRoot<JavaResourceRootProperties>)root).getFile()
                                                                                            .getAbsolutePath());
                                                                                 map2.put("prefix",
                                                                                          ((JpsTypedModuleSourceRoot<JavaResourceRootProperties>)root).getProperties()
                                                                                            .getRelativeOutputPath());
                                                                                 return ant.invokeMethod("zipfileset", new Object[]{map2});
                                                                               }
                                                                             });
                                                                         }
                                                                       });
                                                                   }

                                                                   public void doCall() {
                                                                     doCall(null);
                                                                   }
                                                                 });
                                                           }
                                                         }

                                                         public void doCall() {
                                                           doCall(null);
                                                         }
                                                       });
                                                 }
                                               });
                                             }

                                             public Map<MavenArtifactData, List<JpsModule>> doCall() {
                                               return doCall(null);
                                             }
                                           });
  }

  private static void generatePomXmlFile(String pomXmlPath, MavenArtifactData artifactData) {
    Model model = new Model();


    final Model pomModel = model.setModelVersion("4.0.0") model.setGroupId(artifactData.getCoordinates().getGroupId())
    model.setArtifactId(artifactData.getCoordinates().getArtifactId()) model.setVersion(artifactData.getCoordinates().getVersion());
    DefaultGroovyMethods.each(artifactData.getDependencies(), new Closure<Void>(null, null) {
      public void doCall(Object dep) {
        pomModel.addDependency(createDependencyTag((MavenArtifactDependency)dep));
      }
    });

    File file = new File(pomXmlPath);
    FileUtil.createParentDirs(file);
    ResourceGroovyMethods.withWriter(file, new Closure<Void>(null, null) {
      public void doCall(BufferedWriter it) {
        new MavenXpp3Writer().write(it, pomModel);
      }

      public void doCall() {
        doCall(null);
      }
    });
  }

  private static Dependency createDependencyTag(MavenArtifactDependency dep) {
    Dependency dependency1 = new Dependency();


    final Dependency dependency = dependency1.setGroupId(dep.getCoordinates().getGroupId())
    dependency1.setArtifactId(dep.getCoordinates().getArtifactId()) dependency1.setVersion(dep.getCoordinates().getVersion());
    if (dep.getScope().equals(DependencyScope.RUNTIME)) {
      dependency.setScope("runtime");
    }

    if (dep.getIncludeTransitiveDeps()) {
      DefaultGroovyMethods.each(dep.getExcludedDependencies(), new Closure<Void>(null, null) {
        public void doCall(String it) {
          Exclusion exclusion = new Exclusion();


          dependency.addExclusion(exclusion.setGroupId(StringUtil.substringBefore(it, ":"))
                                  exclusion.setArtifactId(StringUtil.substringAfter(it, ":")));
        }

        public void doCall() {
          doCall(null);
        }
      });
    }
    else {
      Exclusion exclusion = new Exclusion();


      dependency.addExclusion(exclusion.setGroupId("*")exclusion.setArtifactId("*"));
    }

    return ((Dependency)(dependency));
  }

  public static MavenCoordinates generateMavenCoordinatesSquashed(final String moduleName, BuildMessages messages, String version) {
    return generateMavenCoordinates(moduleName + ".squashed", messages, version);
  }

  public static MavenCoordinates generateMavenCoordinates(final String moduleName, BuildMessages messages, String version) {
    final String[] names = moduleName.split("\\.");
    if (DefaultGroovyMethods.size(names) < 2) {
      messages.error("Cannot generate Maven artifacts: incorrect module name \'" + moduleName + "\'");
    }

    String groupId = (String)"com.jetbrains." + DefaultGroovyMethods.join(DefaultGroovyMethods.take(names, 2), ".");
    Integer firstMeaningful = DefaultGroovyMethods.size(names) > 2 && COMMON_GROUP_NAMES.contains(names[1]) ? 2 : 1;
    String artifactId = DefaultGroovyMethods.join(
      DefaultGroovyMethods.collectMany(DefaultGroovyMethods.drop(names, firstMeaningful), new Closure<List<String>>(null, null) {
        public List<String> doCall(String it) {
          return DefaultGroovyMethods.collect(splitByCamelHumpsMergingNumbers(it), new Closure<String>(null, null) {
            public String doCall(String it) { return it.toLowerCase(Locale.US); }

            public String doCall() {
              return doCall(null);
            }
          });
        }

        public List<String> doCall() {
          return doCall(null);
        }
      }), "-");
    return new MavenCoordinates(groupId, artifactId, version);
  }

  private static List<String> splitByCamelHumpsMergingNumbers(String s) {
    String[] words = NameUtilCore.splitNameIntoWords(s);

    ArrayList<String> result = new ArrayList<String>();
    for (int i = 0; ; i < words.length ;){
      String next;
      if (i < words.length - 1 && Character.isDigit(words[i + 1].charAt(0))) {
        next = words[i] + words[i + 1];
        i = i++;
      }
      else {
        next = words[i];
      }

      DefaultGroovyMethods.leftShift(result, next);
    }

    return ((List<String>)(result));
  }

  private Map<JpsModule, MavenArtifactData> generateMavenArtifactData(Collection<String> moduleNames) {
    buildContext.getMessages().debug("Collecting platform modules which can be published as Maven artifacts");
    List<JpsModule> allPlatformModules = DefaultGroovyMethods.collect(moduleNames, new Closure<JpsModule>(this, this) {
      public JpsModule doCall(String it) {
        return buildContext.findRequiredModule(it);
      }

      public JpsModule doCall() {
        return doCall(null);
      }
    });

    final HashMap<JpsModule, MavenArtifactData> results = new HashMap<JpsModule, MavenArtifactData>();
    final HashSet<JpsModule> nonMavenizableModulesSet = new HashSet<JpsModule>();
    final HashSet<JpsModule> computationInProgressSet = new HashSet<JpsModule>();
    DefaultGroovyMethods.each(allPlatformModules, new Closure<MavenArtifactData>(this, this) {
      public MavenArtifactData doCall(JpsModule it) {
        return generateMavenArtifactData(it, results, nonMavenizableModulesSet, computationInProgressSet);
      }

      public MavenArtifactData doCall() {
        return doCall(null);
      }
    });
    return ((Map<JpsModule, MavenArtifactData>)(results));
  }

  public static Map<JpsDependencyElement, DependencyScope> scopedDependencies(JpsModule module) {
    final Map<JpsDependencyElement, DependencyScope> result = new LinkedHashMap<JpsDependencyElement, DependencyScope>();
    DefaultGroovyMethods.each(module.getDependenciesList().getDependencies(), new Closure<DependencyScope>(null, null) {
      public DependencyScope doCall(Object dependency) {
        JpsJavaDependencyExtension extension =
          JpsJavaExtensionService.getInstance().getDependencyExtension((JpsDependencyElement)dependency);
        if (extension == null) return;

        DependencyScope scope;
        switch (extension.getScope()) {
          case PsiEnumConstant:
            COMPILE:
            scope = extension.isExported() ? DependencyScope.COMPILE : DependencyScope.RUNTIME;
            break;
          case PsiEnumConstant:
            RUNTIME:
            scope = DependencyScope.RUNTIME;
            break;
          case PsiEnumConstant:
            PROVIDED:
            return;

          case PsiEnumConstant:
            TEST:
            return;

          default:
            return;
        }
        result.put((JpsDependencyElement)dependency, scope);
        return scope;
      }
    });
    return result;
  }

  private MavenArtifactData generateMavenArtifactData(final JpsModule module,
                                                      final Map<JpsModule, MavenArtifactData> results,
                                                      final Set<JpsModule> nonMavenizableModules,
                                                      final Set<JpsModule> computationInProgress) {
    if (results.containsKey(module)) return ((MavenArtifactData)(results.get(module)));
    if (nonMavenizableModules.contains(module)) return null;
    if (shouldSkipModule(module.getName(), false)) {
      buildContext.getMessages()
        .warning("  module \'" + module.getName() + "\' doesn\'t belong to IntelliJ project so it cannot be published");
      return null;
    }

    ScrambleTool scrambleTool = buildContext.getProprietaryBuildTools().getScrambleTool();
    if (scrambleTool != null && scrambleTool.getNamesOfModulesRequiredToBeScrambled().contains(module.getName())) {
      buildContext.getMessages().warning("  module \'" + module.getName() + "\' must be scrambled so it cannot be published");
      return null;
    }


    final Reference<Boolean> mavenizable = new Reference<boolean>(true);
    DefaultGroovyMethods.leftShift(computationInProgress, module);
    final List<MavenArtifactDependency> dependencies = new ArrayList<MavenArtifactDependency>();
    DefaultGroovyMethods.each(scopedDependencies(module), new Closure<Object>(this, this) {
      public Object doCall(Object dependency, Object scope) {
        if (dependency instanceof JpsModuleDependency) {
          JpsModule depModule = (DefaultGroovyMethods.asType(dependency, JpsModuleDependency.class)).getModule();
          if (shouldSkipModule(depModule.getName(), true)) return;

          if (computationInProgress.contains(depModule)) {
          /*
           It's forbidden to have compile-time circular dependencies in IntelliJ project, but there are some cycles with runtime scope
            (e.g. intellij.platform.ide.impl depends on (runtime scope) intellij.platform.configurationStore.impl which depends on intellij.platform.ide.impl).
           It's convenient to have such dependencies to allow running tests in classpath of their modules, so we can just ignore them while
           generating pom.xml files.
          */
            buildContext.getMessages()
              .warning(" module \'" + module.getName() + "\': skip recursive dependency on \'" + depModule.getName() + "\'");
          }
          else {
            MavenArtifactData depArtifact = generateMavenArtifactData(depModule, results, nonMavenizableModules, computationInProgress);
            if (depArtifact == null) {
              buildContext.getMessages().warning(" module \'" +
                                                 module.getName() +
                                                 "\' depends on non-mavenizable module \'" +
                                                 depModule.getName() +
                                                 "\' so it cannot be published");
              mavenizable.set(false);
              return;
            }

            return DefaultGroovyMethods.leftShift(dependencies,
                                                  new MavenArtifactDependency(depArtifact.getCoordinates(), true, new ArrayList<String>(),
                                                                              (DependencyScope)scope));
          }
        }
        else if (dependency instanceof JpsLibraryDependency) {
          final JpsLibrary library = (DefaultGroovyMethods.asType(dependency, JpsLibraryDependency.class)).getLibrary();
          JpsTypedLibrary<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>> typed = library.asTyped(JpsRepositoryLibraryType.INSTANCE);
          if (typed != null) {
            return DefaultGroovyMethods.leftShift(dependencies, createArtifactDependencyByLibrary(typed.getProperties().getData(),
                                                                                                  (DependencyScope)scope));
          }
          else if (!isOptionalDependency(library)) {
            buildContext.getMessages().warning(
              " module \'" + module.getName() + "\' depends on non-maven library " + LibraryLicensesListGenerator.getLibraryName(library));
            return setGroovyRef(mavenizable, false);
          }
        }
      }
    });
    computationInProgress.remove(module);
    if (!mavenizable.get()) {
      DefaultGroovyMethods.leftShift(nonMavenizableModules, module);
      return null;
    }

    MavenArtifactData artifactData = new MavenArtifactData(generateMavenCoordinatesForModule(module), dependencies);
    results.put(module, artifactData);
    return artifactData;
  }

  protected boolean shouldSkipModule(String moduleName, boolean moduleIsDependency) {
    if (skipNothing || moduleIsDependency) return false;
    return !moduleName.startsWith("intellij.");
  }

  protected MavenCoordinates generateMavenCoordinatesForModule(JpsModule module) {
    return generateMavenCoordinates(module.getName(), buildContext.getMessages(), buildContext.getBuildNumber());
  }

  private MavenCoordinates generateMavenCoordinatesForSquashedModule(JpsModule module) {
    return generateMavenCoordinatesSquashed(module.getName(), buildContext.getMessages(), buildContext.getBuildNumber());
  }

  public static boolean isOptionalDependency(JpsLibrary library) {
    //todo: this is a temporary workaround until these libraries are published to Maven repository;
    // it's unlikely that code which depend on these libraries will be used when running tests so skipping these dependencies shouldn't cause real problems.
    //  'microba' contains UI elements which are used in few places (IDEA-200834),
    //  'precompiled_jshell-frontend' is used by "JShell Console" action only (IDEA-222381).
    return library.getName().equals("microba") || library.getName().equals("jshell-frontend");
  }

  private static MavenArtifactDependency createArtifactDependencyByLibrary(JpsMavenRepositoryLibraryDescriptor descriptor,
                                                                           DependencyScope scope) {
    return new MavenArtifactDependency(new MavenCoordinates(descriptor.getGroupId(), descriptor.getArtifactId(), descriptor.getVersion()),
                                       descriptor.isIncludeTransitiveDependencies(), descriptor.getExcludedDependencies(), scope);
  }

  public static Dependency createDependencyTagByLibrary(JpsMavenRepositoryLibraryDescriptor descriptor) {
    return createDependencyTag(createArtifactDependencyByLibrary(descriptor, DependencyScope.COMPILE));
  }

  /**
   * second component of module names which describes a common group rather than a specific framework and therefore should be excluded from artifactId
   */
  private static final Set<String> COMMON_GROUP_NAMES =
    DefaultGroovyMethods.asType(new ArrayList<String>(Arrays.asList("platform", "vcs", "tools", "clouds")), Set.class);
  protected final BuildContext buildContext;
  private final boolean skipNothing;

  public static enum DependencyScope {
    COMPILE, RUNTIME;
  }

  private static <K, V, Value extends V> Value putAt0(Map<K, V> propOwner, K key, Value value) {
    propOwner.put(key, value);
    return value;
  }

  private static <T> T setGroovyRef(Reference<T> ref, T newValue) {
    ref.set(newValue);
    return newValue;
  }
}
