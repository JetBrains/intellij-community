/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.scratch;

import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.application.AbstractApplicationConfigurationProducer;
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.ide.scratch.ScratchFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public class JavaScratchConfigurationProducer extends AbstractApplicationConfigurationProducer<JavaScratchConfiguration> {

  public JavaScratchConfigurationProducer() {
    super(JavaScratchConfigurationType.getInstance());
  }

  @Override
  protected boolean setupConfigurationFromContext(JavaScratchConfiguration configuration, ConfigurationContext context, Ref<PsiElement> sourceElement) {
    final Location location = context.getLocation();
    if (location != null) {
      final VirtualFile vFile = location.getVirtualFile();
      if (vFile instanceof VirtualFileWithId && vFile.getFileType() == ScratchFileType.INSTANCE) {
        final PsiFile psiFile = location.getPsiElement().getContainingFile();
        if (psiFile != null && psiFile.getLanguage() == JavaLanguage.INSTANCE) {
          configuration.SCRATCH_FILE_ID = ((VirtualFileWithId)vFile).getId();
          return super.setupConfigurationFromContext(configuration, context, sourceElement);
        }
      }
    }
    return false;
  }

  @Override
  public boolean shouldReplace(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    return other.isProducedBy(AbstractApplicationConfigurationProducer.class) && !other.isProducedBy(JavaScratchConfigurationProducer.class);
  }

  @Override
  public boolean isConfigurationFromContext(JavaScratchConfiguration configuration, ConfigurationContext context) {
    final PsiElement location = context.getPsiLocation();
    final PsiClass aClass = ApplicationConfigurationType.getMainClass(location);
    if (aClass != null && Comparing.equal(JavaExecutionUtil.getRuntimeQualifiedName(aClass), configuration.getMainClassName())) {
      // for scratches it is enough to check that the configuration is associated with the same scratch file
      final VirtualFile scratchFile = configuration.getScratchVirtualFile();
      if (scratchFile != null) {
        final PsiFile containingFile = aClass.getContainingFile();
        if (containingFile != null && scratchFile.equals(containingFile.getVirtualFile())) {
          return true;
        }
      }
    }
    return false;
  }

}
