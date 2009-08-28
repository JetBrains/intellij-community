package com.intellij.testIntegration;

import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public interface TestFrameworkDescriptor {
  ExtensionPointName<TestFrameworkDescriptor> EXTENSION_NAME = ExtensionPointName.create("com.intellij.testFrameworkDescriptor");

  String getName();

  boolean isLibraryAttached(Module m);

  String getLibraryPath();

  @Nullable
  String getDefaultSuperClass();

  boolean isTestClass(PsiElement element);

  FileTemplateDescriptor getSetUpMethodFileTemplateDescriptor();

  FileTemplateDescriptor getTearDownMethodFileTemplateDescriptor();

  FileTemplateDescriptor getTestMethodFileTemplateDescriptor();
}
