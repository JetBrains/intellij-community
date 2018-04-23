/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.core;

import com.intellij.codeInsight.ContainerProvider;
import com.intellij.codeInsight.JavaContainerProvider;
import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.codeInsight.folding.impl.JavaCodeFoldingSettingsBase;
import com.intellij.codeInsight.folding.impl.JavaFoldingBuilderBase;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.LanguageASTFactory;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.folding.LanguageFolding;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.fileTypes.PlainTextParserDefinition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.psi.*;
import com.intellij.psi.impl.EmptySubstitutorImpl;
import com.intellij.psi.impl.LanguageConstantExpressionEvaluator;
import com.intellij.psi.impl.PsiExpressionEvaluator;
import com.intellij.psi.impl.compiled.ClassFileStubBuilder;
import com.intellij.psi.impl.file.PsiPackageImplementationHelper;
import com.intellij.psi.impl.search.MethodSuperSearcher;
import com.intellij.psi.impl.source.tree.CoreJavaASTFactory;
import com.intellij.psi.impl.source.tree.PlainTextASTFactory;
import com.intellij.psi.presentation.java.*;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.stubs.BinaryFileStubBuilders;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
@SuppressWarnings("UnusedDeclaration") // Upsource and Kotlin
public class JavaCoreApplicationEnvironment extends CoreApplicationEnvironment {
  public JavaCoreApplicationEnvironment(@NotNull Disposable parentDisposable) {
    this(parentDisposable, true);
  }

  public JavaCoreApplicationEnvironment(@NotNull Disposable parentDisposable, boolean unitTestMode) {
    super(parentDisposable, unitTestMode);

    registerFileType(JavaClassFileType.INSTANCE, "class");
    registerFileType(JavaFileType.INSTANCE, "java");
    registerFileType(ArchiveFileType.INSTANCE, "jar;zip");
    registerFileType(PlainTextFileType.INSTANCE, "txt;sh;bat;cmd;policy;log;cgi;MF;jad;jam;htaccess");

    addExplicitExtension(LanguageASTFactory.INSTANCE, PlainTextLanguage.INSTANCE, new PlainTextASTFactory());
    addExplicitExtension(LanguageParserDefinitions.INSTANCE, PlainTextLanguage.INSTANCE, new PlainTextParserDefinition());

    addExplicitExtension(FileTypeFileViewProviders.INSTANCE, JavaClassFileType.INSTANCE,  new ClassFileViewProviderFactory());
    addExplicitExtension(BinaryFileStubBuilders.INSTANCE, JavaClassFileType.INSTANCE, new ClassFileStubBuilder());

    addExplicitExtension(LanguageASTFactory.INSTANCE, JavaLanguage.INSTANCE, new CoreJavaASTFactory());
    addExplicitExtension(LanguageParserDefinitions.INSTANCE, JavaLanguage.INSTANCE, new JavaParserDefinition());
    addExplicitExtension(LanguageConstantExpressionEvaluator.INSTANCE, JavaLanguage.INSTANCE, new PsiExpressionEvaluator());

    addExtension(ContainerProvider.EP_NAME, new JavaContainerProvider());

    myApplication.registerService(PsiPackageImplementationHelper.class, new CorePsiPackageImplementationHelper());

    myApplication.registerService(EmptySubstitutor.class, new EmptySubstitutorImpl());
    myApplication.registerService(JavaDirectoryService.class, createJavaDirectoryService());
    myApplication.registerService(JavaVersionService.class, new JavaVersionService());

    addExplicitExtension(ItemPresentationProviders.INSTANCE, PsiPackage.class, new PackagePresentationProvider());
    addExplicitExtension(ItemPresentationProviders.INSTANCE, PsiClass.class, new ClassPresentationProvider());
    addExplicitExtension(ItemPresentationProviders.INSTANCE, PsiMethod.class, new MethodPresentationProvider());
    addExplicitExtension(ItemPresentationProviders.INSTANCE, PsiField.class, new FieldPresentationProvider());
    addExplicitExtension(ItemPresentationProviders.INSTANCE, PsiLocalVariable.class, new VariablePresentationProvider());
    addExplicitExtension(ItemPresentationProviders.INSTANCE, PsiParameter.class, new VariablePresentationProvider());

    registerApplicationService(JavaCodeFoldingSettings.class, new JavaCodeFoldingSettingsBase());
    addExplicitExtension(LanguageFolding.INSTANCE, JavaLanguage.INSTANCE, new JavaFoldingBuilderBase() {
      @Override
      protected boolean shouldShowExplicitLambdaType(@NotNull PsiAnonymousClass anonymousClass, @NotNull PsiNewExpression expression) {
        return false;
      }

      @Override
      protected boolean isBelowRightMargin(@NotNull PsiFile file, int lineLength) {
        return false;
      }
    });

    registerApplicationExtensionPoint(SuperMethodsSearch.EP_NAME, QueryExecutor.class);
    addExtension(SuperMethodsSearch.EP_NAME, new MethodSuperSearcher());
  }

  @SuppressWarnings("MethodMayBeStatic") // overridden in upsource
  protected CoreJavaDirectoryService createJavaDirectoryService() {
    return new CoreJavaDirectoryService();
  }
}
