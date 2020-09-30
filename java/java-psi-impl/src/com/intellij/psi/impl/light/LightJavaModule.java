// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.light;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.util.ObjectUtils.notNull;

public final class LightJavaModule extends LightElement implements PsiJavaModule {
  private final LightJavaModuleReferenceElement myRefElement;
  private final VirtualFile myRoot;
  private final NotNullLazyValue<List<PsiPackageAccessibilityStatement>> myExports = AtomicNotNullLazyValue.createValue(() -> findExports());

  private LightJavaModule(@NotNull PsiManager manager, @NotNull VirtualFile root, @NotNull String name) {
    super(manager, JavaLanguage.INSTANCE);
    myRoot = root;
    myRefElement = new LightJavaModuleReferenceElement(manager, name);
  }

  @NotNull
  public VirtualFile getRootVirtualFile() {
    return myRoot;
  }

  @Nullable
  @Override
  public PsiDocComment getDocComment() {
    return null;
  }

  @NotNull
  @Override
  public Iterable<PsiRequiresStatement> getRequires() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public Iterable<PsiPackageAccessibilityStatement> getExports() {
    return myExports.getValue();
  }

  private List<PsiPackageAccessibilityStatement> findExports() {
    List<PsiPackageAccessibilityStatement> exports = new ArrayList<>();

    VfsUtilCore.visitChildrenRecursively(myRoot, new VirtualFileVisitor<Void>() {
      private final JavaDirectoryService service = JavaDirectoryService.getInstance();

      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (file.isDirectory() && !myRoot.equals(file)) {
          PsiDirectory directory = getManager().findDirectory(file);
          if (directory != null) {
            PsiPackage pkg = service.getPackage(directory);
            if (pkg != null) {
              String packageName = pkg.getQualifiedName();
              if (!packageName.isEmpty() && !PsiUtil.isPackageEmpty(new PsiDirectory[]{directory}, packageName)) {
                exports.add(new LightPackageAccessibilityStatement(getManager(), packageName));
              }
            }
          }
        }
        return true;
      }
    });

    return exports;
  }

  @NotNull
  @Override
  public Iterable<PsiPackageAccessibilityStatement> getOpens() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public Iterable<PsiUsesStatement> getUses() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public Iterable<PsiProvidesStatement> getProvides() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public PsiJavaModuleReferenceElement getNameIdentifier() {
    return myRefElement;
  }

  @NotNull
  @Override
  public String getName() {
    return myRefElement.getReferenceText();
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Cannot modify an automatic module '" + getName() + "'");
  }

  @Override
  public PsiModifierList getModifierList() {
    return null;
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return false;
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProviders.getItemPresentation(this);
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return notNull(myManager.findDirectory(myRoot), super.getNavigationElement());
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof LightJavaModule && myRoot.equals(((LightJavaModule)obj).myRoot) && getManager() == ((LightJavaModule)obj).getManager();
  }

  @Override
  public int hashCode() {
    return getName().hashCode() * 31 + getManager().hashCode();
  }

  @Override
  public String toString() {
    return "PsiJavaModule:" + getName();
  }

  private static final class LightJavaModuleReferenceElement extends LightElement implements PsiJavaModuleReferenceElement {
    private final String myText;

    private LightJavaModuleReferenceElement(@NotNull PsiManager manager, @NotNull String text) {
      super(manager, JavaLanguage.INSTANCE);
      myText = text;
    }

    @NotNull
    @Override
    public String getReferenceText() {
      return myText;
    }

    @Override
    public PsiJavaModuleReference getReference() {
      return null;
    }

    @Override
    public String toString() {
      return "PsiJavaModuleReference";
    }
  }

  private static class LightPackageAccessibilityStatement extends LightElement implements PsiPackageAccessibilityStatement {
    private final String myPackageName;

    LightPackageAccessibilityStatement(@NotNull PsiManager manager, @NotNull String packageName) {
      super(manager, JavaLanguage.INSTANCE);
      myPackageName = packageName;
    }

    @NotNull
    @Override
    public Role getRole() {
      return Role.EXPORTS;
    }

    @Nullable
    @Override
    public PsiJavaCodeReferenceElement getPackageReference() {
      return null;
    }

    @Nullable
    @Override
    public String getPackageName() {
      return myPackageName;
    }

    @NotNull
    @Override
    public Iterable<PsiJavaModuleReferenceElement> getModuleReferences() {
      return Collections.emptyList();
    }

    @NotNull
    @Override
    public List<String> getModuleNames() {
      return Collections.emptyList();
    }

    @Override
    public String toString() {
      return "PsiPackageAccessibilityStatement";
    }
  }

  /** @deprecated method scope was extended, use {@link #findModule} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  public static @NotNull LightJavaModule getModule(@NotNull PsiManager manager, @NotNull VirtualFile root) {
    LightJavaModule module = findModule(manager, root);
    assert module != null : root;
    return module;
  }

  /** The method is expected to be called on roots obtained from JavaAutoModuleNameIndex/JavaSourceModuleNameIndex */
  public static @Nullable LightJavaModule findModule(@NotNull PsiManager manager, @NotNull VirtualFile root) {
    PsiElement directory = manager.findDirectory(root);
    if (directory == null) return null;
    if (root.isInLocalFileSystem()) {
      return CachedValuesManager.getCachedValue(directory, () -> {
        VirtualFile manifest = root.findFileByRelativePath(JarFile.MANIFEST_NAME);
        if (manifest != null) {
          PsiElement file = manager.findFile(manifest);
          if (file != null) {
            String name = claimedModuleName(manifest);
            LightJavaModule module = name != null ? new LightJavaModule(manager, root, name) : null;
            return CachedValueProvider.Result.create(module, file);
          }
        }
        return CachedValueProvider.Result.create(null, PsiModificationTracker.MODIFICATION_COUNT);
      });
    }
    else {
      return CachedValuesManager.getCachedValue(directory, () -> {
        LightJavaModule module = new LightJavaModule(manager, root, moduleName(root));
        return CachedValueProvider.Result.create(module, directory);
      });
    }
  }

  @NotNull
  public static String moduleName(@NotNull VirtualFile jarRoot) {
    VirtualFile manifest = jarRoot.findFileByRelativePath(JarFile.MANIFEST_NAME);
    if (manifest != null) {
      String claimed = claimedModuleName(manifest);
      if (claimed != null) return claimed;
    }

    return moduleName(jarRoot.getNameWithoutExtension());
  }

  @Nullable
  public static String claimedModuleName(@NotNull VirtualFile manifest) {
    try (InputStream stream = manifest.getInputStream()) {
      return new Manifest(stream).getMainAttributes().getValue(PsiJavaModule.AUTO_MODULE_NAME);
    }
    catch (IOException e) {
      Logger.getInstance(LightJavaModule.class).warn(manifest.getPath(), e);
      return null;
    }
  }

  /**
   * <p>Implements a name deriving for automatic modules as described in ModuleFinder.of(Path...) method documentation.</p>
   *
   * <p>Please note that the result may not be a valid module name when the source contains a sequence that starts with a digit
   * (e.g. "org.7gnomes..."). One may validate the result with {@link PsiNameHelper#isValidModuleName}.</p>
   *
   * @param name a .jar file name without extension
   * @see <a href="http://docs.oracle.com/javase/9/docs/api/java/lang/module/ModuleFinder.html#of-java.nio.file.Path...-">ModuleFinder.of(Path...)</a>
   */
  @NotNull
  public static String moduleName(@NotNull String name) {
    // If the name matches the regular expression "-(\\d+(\\.|$))" then the module name will be derived from the sub-sequence
    // preceding the hyphen of the first occurrence.
    Matcher m = Patterns.VERSION.matcher(name);
    if (m.find()) {
      name = name.substring(0, m.start());
    }

    // All non-alphanumeric characters ([^A-Za-z0-9]) are replaced with a dot (".") ...
    name = Patterns.NON_NAME.matcher(name).replaceAll(".");
    // ... all repeating dots are replaced with one dot ...
    name = Patterns.DOT_SEQUENCE.matcher(name).replaceAll(".");
    // ... and all leading and trailing dots are removed.
    name = StringUtil.trimLeading(StringUtil.trimTrailing(name, '.'), '.');

    return name;
  }

  private static class Patterns {
    private static final Pattern VERSION = Pattern.compile("-(\\d+(\\.|$))");
    private static final Pattern NON_NAME = Pattern.compile("[^A-Za-z0-9]");
    private static final Pattern DOT_SEQUENCE = Pattern.compile("\\.{2,}");
  }
}