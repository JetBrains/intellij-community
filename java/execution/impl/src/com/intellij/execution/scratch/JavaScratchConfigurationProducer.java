// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.scratch;

import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.application.AbstractApplicationConfigurationProducer;
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author Eugene Zhuravlev
 */
public class JavaScratchConfigurationProducer extends AbstractApplicationConfigurationProducer<JavaScratchConfiguration> {
  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return JavaScratchConfigurationType.getInstance();
  }

  @Override
  protected boolean setupConfigurationFromContext(@NotNull JavaScratchConfiguration configuration,
                                                  @NotNull ConfigurationContext context,
                                                  @NotNull Ref<PsiElement> sourceElement) {
    final Location location = context.getLocation();
    if (location != null) {
      final VirtualFile vFile = location.getVirtualFile();
      if (vFile != null && ScratchUtil.isScratch(vFile)) {
        final PsiFile psiFile = location.getPsiElement().getContainingFile();
        if (psiFile != null && psiFile.getLanguage() == JavaLanguage.INSTANCE) {
          configuration.setScratchFileUrl(vFile.getUrl());
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
  public boolean isConfigurationFromContext(@NotNull JavaScratchConfiguration configuration, @NotNull ConfigurationContext context) {
    final PsiElement location = context.getPsiLocation();
    final PsiClass aClass = ApplicationConfigurationType.getMainClass(location);
    if (aClass != null && Objects.equals(JavaExecutionUtil.getRuntimeQualifiedName(aClass), configuration.getMainClassName())) {
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
