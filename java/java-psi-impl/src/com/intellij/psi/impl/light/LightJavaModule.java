/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.light;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.util.ObjectUtils.notNull;

public class LightJavaModule extends LightElement implements PsiJavaModule {
  private final LightJavaModuleReferenceElement myRefElement;
  private final VirtualFile myJarRoot;

  private LightJavaModule(@NotNull PsiManager manager, @NotNull VirtualFile jarRoot) {
    super(manager, JavaLanguage.INSTANCE);
    myJarRoot = jarRoot;
    myRefElement = new LightJavaModuleReferenceElement(manager, moduleName(jarRoot.getNameWithoutExtension()));
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
  public PsiJavaModuleReferenceElement getNameElement() {
    return myRefElement;
  }

  @NotNull
  @Override
  public String getModuleName() {
    return myRefElement.getReferenceText();
  }

  @NotNull
  @Override
  public Iterable<PsiRequiresStatement> getRequires() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public Iterable<PsiExportsStatement> getExports() {
    return Collections.emptyList();
  }

  @Override
  public String getName() {
    return getModuleName();
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Cannot modify automatic module '" + getName() + "'");
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
    return getModuleName().hashCode() * 31 + getManager().hashCode();
  }

  @Override
  public String toString() {
    return "PsiJavaModule:" + getModuleName();
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

  @NotNull
  public static LightJavaModule getModule(@NotNull final PsiManager manager, @NotNull final VirtualFile jarRoot) {
    final PsiDirectory directory = manager.findDirectory(jarRoot);
    assert directory != null : jarRoot;
    return CachedValuesManager.getCachedValue(directory, new CachedValueProvider<LightJavaModule>() {
      @Nullable
      @Override
      public Result<LightJavaModule> compute() {
        LightJavaModule module = new LightJavaModule(manager, jarRoot);
        return Result.create(module, directory);
      }
    });
  }

  /**
   * Implements a name deriving for  automatic modules as described in ModuleFinder.of(Path...) method documentation.
   *
   * @param name a .jar file name without extension
   * @see <a href="http://download.java.net/java/jdk9/docs/api/java/lang/module/ModuleFinder.html#of-java.nio.file.Path...-">ModuleFinder.of(Path...)</a>
   */
  @NotNull
  public static String moduleName(@NotNull String name) {
    // If the name matches the regular expression "-(\\d+(\\.|$))" then the module name will be derived from the sub-sequence
    // preceding the hyphen of the first occurrence.
    Matcher m = Patterns.VERSION.matcher(name);
    if (m.find()) {
      name = name.substring(0, m.start());
    }

    // For the module name, then any trailing digits and dots are removed ...
    name = Patterns.TAIL_VERSION.matcher(name).replaceFirst("");
    // ... all non-alphanumeric characters ([^A-Za-z0-9]) are replaced with a dot (".") ...
    name = Patterns.NON_NAME.matcher(name).replaceAll(".");
    // ... all repeating dots are replaced with one dot ...
    name = Patterns.DOT_SEQUENCE.matcher(name).replaceAll(".");
    // ... and all leading and trailing dots are removed
    name = StringUtil.trimLeading(StringUtil.trimTrailing(name, '.'), '.');

    return name;
  }

  private static class Patterns {
    private static final Pattern VERSION = Pattern.compile("-(\\d+(\\.|$))");
    private static final Pattern TAIL_VERSION = Pattern.compile("[0-9.]+$");
    private static final Pattern NON_NAME = Pattern.compile("[^A-Za-z0-9]");
    private static final Pattern DOT_SEQUENCE = Pattern.compile("\\.{2,}");
  }
}