// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.JavaTestUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceRegistrarImpl;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FilePathReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.AssertionFailedError;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.util.List;

public abstract class CreateFileQuickFixTestCase extends LightJavaCodeInsightFixtureTestCase {

  protected static class StandardContentRootsProjectDescriptor extends ProjectDescriptor {

    public StandardContentRootsProjectDescriptor() {
      super(LanguageLevel.HIGHEST);
    }

    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);

      contentEntry.clearSourceFolders();

      String contentEntryUrl = contentEntry.getUrl();

      contentEntry.addSourceFolder(contentEntryUrl + "/main/java", JavaSourceRootType.SOURCE);
      contentEntry.addSourceFolder(contentEntryUrl + "/main/resources", JavaResourceRootType.RESOURCE);

      JavaSourceRootProperties generatedProperties = JavaSourceRootType.SOURCE.createDefaultProperties();
      generatedProperties.setForGeneratedSources(true);
      contentEntry.addSourceFolder(contentEntryUrl + "/main/gen", JavaSourceRootType.SOURCE, generatedProperties);

      contentEntry.addSourceFolder(contentEntryUrl + "/test/java", JavaSourceRootType.TEST_SOURCE);
      contentEntry.addSourceFolder(contentEntryUrl + "/test/resources", JavaResourceRootType.TEST_RESOURCE);
    }
  }

  public static final StandardContentRootsProjectDescriptor STANDARD_CONTENT_ROOTS_DESCRIPTOR = new StandardContentRootsProjectDescriptor();

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return STANDARD_CONTENT_ROOTS_DESCRIPTOR;
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + getTestCasePath();
  }

  protected abstract String getTestCasePath();

  protected void withFileReferenceInStringLiteral(Runnable action) {
    PsiReferenceRegistrarImpl referenceProvidersRegistry =
      (PsiReferenceRegistrarImpl)ReferenceProvidersRegistry.getInstance().getRegistrar(JavaLanguage.INSTANCE);
    PsiReferenceProvider fileReferenceProvider = new FilePathReferenceProvider();

    try {
      referenceProvidersRegistry.registerReferenceProvider(PlatformPatterns.psiElement(PsiLiteralExpression.class), fileReferenceProvider);
      getPsiManager().dropPsiCaches();
      action.run();
    }
    finally {
      referenceProvidersRegistry.unregisterReferenceProvider(PsiLiteralExpression.class, fileReferenceProvider);
    }
  }

  @NotNull
  protected FileReference findFileReference(@Nullable PsiReference ref) {
    if (ref instanceof FileReference) {
      return (FileReference)ref;
    }
    if (ref instanceof PsiMultiReference) {
      List<PsiReference> filtered = ContainerUtil.filter(((PsiMultiReference)ref).getReferences(),
                                                         value -> value instanceof FileReference);
      if (!filtered.isEmpty()) {
        return (FileReference)filtered.get(0);
      }
    }
    throw new AssertionFailedError("Unable to find FileReference");
  }
}
