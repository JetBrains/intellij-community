/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
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
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.util.ObjectUtils.notNull;

public class LightJavaModule extends LightElement implements PsiJavaModule {
  private final LightJavaModuleReferenceElement myRefElement;
  private final VirtualFile myJarRoot;
  private final NotNullLazyValue<List<PsiPackageAccessibilityStatement>> myExports = AtomicNotNullLazyValue.createValue(() -> findExports());

  private LightJavaModule(@NotNull PsiManager manager, @NotNull VirtualFile jarRoot) {
    super(manager, JavaLanguage.INSTANCE);
    myJarRoot = jarRoot;
    myRefElement = new LightJavaModuleReferenceElement(manager, moduleName(jarRoot));
  }

  @NotNull
  public VirtualFile getRootVirtualFile() {
    return myJarRoot;
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
    List<PsiPackageAccessibilityStatement> exports = ContainerUtil.newArrayList();

    VfsUtilCore.visitChildrenRecursively(myJarRoot, new VirtualFileVisitor() {
      private JavaDirectoryService service = JavaDirectoryService.getInstance();

      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (file.isDirectory() && !myJarRoot.equals(file)) {
          PsiDirectory directory = myManager.findDirectory(file);
          if (directory != null) {
            PsiPackage pkg = service.getPackage(directory);
            if (pkg != null) {
              String packageName = pkg.getQualifiedName();
              if (!packageName.isEmpty() && !PsiUtil.isPackageEmpty(new PsiDirectory[]{directory}, packageName)) {
                exports.add(new LightPackageAccessibilityStatement(myManager, packageName));
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
    throw new IncorrectOperationException("Cannot modify automatic module '" + getName() + "'");
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
    return notNull(myManager.findDirectory(myJarRoot), super.getNavigationElement());
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof LightJavaModule && myJarRoot.equals(((LightJavaModule)obj).myJarRoot) && getManager() == ((LightJavaModule)obj).getManager();
  }

  @Override
  public int hashCode() {
    return getName().hashCode() * 31 + getManager().hashCode();
  }

  @Override
  public String toString() {
    return "PsiJavaModule:" + getName();
  }

  private static class LightJavaModuleReferenceElement extends LightElement implements PsiJavaModuleReferenceElement {
    private final String myText;

    public LightJavaModuleReferenceElement(@NotNull PsiManager manager, @NotNull String text) {
      super(manager, JavaLanguage.INSTANCE);
      myText = text;
    }

    @NotNull
    @Override
    public String getReferenceText() {
      return myText;
    }

    @Nullable
    @Override
    public PsiPolyVariantReference getReference() {
      return null;
    }

    @Override
    public String toString() {
      return "PsiJavaModuleReference";
    }
  }

  private static class LightPackageAccessibilityStatement extends LightElement implements PsiPackageAccessibilityStatement {
    private final String myPackageName;

    public LightPackageAccessibilityStatement(@NotNull PsiManager manager, @NotNull String packageName) {
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

  @NotNull
  public static LightJavaModule getModule(@NotNull PsiManager manager, @NotNull VirtualFile jarRoot) {
    PsiDirectory directory = manager.findDirectory(jarRoot);
    assert directory != null : jarRoot;
    return CachedValuesManager.getCachedValue(directory, () -> {
      LightJavaModule module = new LightJavaModule(manager, jarRoot);
      return CachedValueProvider.Result.create(module, directory);
    });
  }

  @NotNull
  public static String moduleName(@NotNull VirtualFile jarRoot) {
    VirtualFile manifest = jarRoot.findFileByRelativePath(JarFile.MANIFEST_NAME);
    if (manifest != null) {
      try (InputStream stream = manifest.getInputStream()) {
        String claimed = new Manifest(stream).getMainAttributes().getValue("Automatic-Module-Name");
        if (claimed != null) return claimed;
      }
      catch (IOException e) {
        Logger.getInstance(LightJavaModule.class).warn(e);
      }
    }

    return moduleName(jarRoot.getNameWithoutExtension());
  }

  /**
   * Implements a name deriving for automatic modules as described in ModuleFinder.of(Path...) method documentation.
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