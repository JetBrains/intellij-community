/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.compiler;

import com.intellij.module.ModuleGroupTestsKt;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;
import org.jetbrains.jps.model.java.impl.compiler.ProcessorConfigProfileImpl;

import java.util.Arrays;
import java.util.List;

public class CompilerConfigurationTest extends PlatformTestCase {
  public void testUpdateTargetLevelOnModuleRename() {
    Module module = createModule("foo");
    getConfiguration().setBytecodeTargetLevel(module, "1.6");

    ModuleGroupTestsKt.renameModule(module, "bar");

    assertEquals("1.6", getConfiguration().getBytecodeTargetLevel(module));
  }

  public void testUpdateOptionsOnModuleRename() {
    Module module = createModule("foo");
    List<String> options = Arrays.asList("-nowarn");
    getConfiguration().setAdditionalOptions(module, options);

    ModuleGroupTestsKt.renameModule(module, "bar");

    assertEquals(options, getConfiguration().getAdditionalOptions(module));
  }

  public void testUpdateAnnotationsProfilesOnModuleRename() {
    Module module = createModule("foo");
    ProcessorConfigProfileImpl profile = new ProcessorConfigProfileImpl("foo");
    profile.addModuleName("foo");
    getConfiguration().addModuleProcessorProfile(profile);
    assertSame(profile, getConfiguration().getAnnotationProcessingConfiguration(module));

    ModuleGroupTestsKt.renameModule(module, "bar");

    ProcessorConfigProfile newProfile = getConfiguration().getAnnotationProcessingConfiguration(module);
    assertNotNull(newProfile);
    assertEquals("bar", assertOneElement(newProfile.getModuleNames()));
  }

  private CompilerConfigurationImpl getConfiguration() {
    return (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject);
  }
}
