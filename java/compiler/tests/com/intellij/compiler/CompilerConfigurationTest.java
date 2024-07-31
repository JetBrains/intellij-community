// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler;

import com.intellij.module.ModuleGroupTestsKt;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.testFramework.HeavyPlatformTestCase;
import org.jdom.JDOMException;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

public class CompilerConfigurationTest extends HeavyPlatformTestCase {
  public void testUpdateTargetLevelOnModuleRename() {
    Module module = createModule("foo");
    getConfiguration().setBytecodeTargetLevel(module, "1.6");

    ModuleGroupTestsKt.renameModule(module, "bar");

    assertEquals("1.6", getConfiguration().getBytecodeTargetLevel(module));
  }

  public void testLoadState() throws IOException, JDOMException {
    Module module = createModule("foo");
    CompilerConfigurationImpl configuration = getConfiguration();
    configuration.setBytecodeTargetLevel(module, "1.6");
    assertThat(configuration.getState()).isEqualTo("""
                                                     <state>
                                                       <bytecodeTargetLevel>
                                                         <module name="foo" target="1.6" />
                                                       </bytecodeTargetLevel>
                                                     </state>""");

    configuration.loadState(JDOMUtil.load("""
                                            <state>
                                              <bytecodeTargetLevel>
                                                <module name="foo" target="1.7" />
                                              </bytecodeTargetLevel>
                                            </state>"""));

    assertThat(configuration.getBytecodeTargetLevel(module)).isEqualTo("1.7");
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
    ProcessorConfigProfile profile = getConfiguration().addNewProcessorProfile("foo");
    profile.addModuleName("foo");
    assertSame(profile, getConfiguration().getAnnotationProcessingConfiguration(module));

    ModuleGroupTestsKt.renameModule(module, "bar");

    ProcessorConfigProfile newProfile = getConfiguration().getAnnotationProcessingConfiguration(module);
    assertNotNull(newProfile);
    assertEquals("bar", assertOneElement(newProfile.getModuleNames()));
  }

  public void testDefaultParallelCompilationOptionIsAutomatic() {
    assertEquals(getConfiguration().getParallelCompilationOption(), ParallelCompilationOption.AUTOMATIC);
  }

  public void testDefaultParallelCompilationOptionDoesNotChangeXml() {
    CompilerConfigurationImpl configuration = getConfiguration();

    assertNotNull(configuration.getParallelCompilationOption());
    assertThat(configuration.getState()).isEqualTo("<state />");
  }

  public void testChangedInOldWayParallelCompilationOptionChangesXml() {
    CompilerConfigurationImpl configuration = getConfiguration();

    configuration.setParallelCompilationEnabled(true);

    assertEquals(configuration.getParallelCompilationOption(), ParallelCompilationOption.ENABLED);
    assertThat(configuration.getState()).isEqualTo(
     """
     <state>
       <option name="PARALLEL_COMPILATION_OPTION" value="Enabled" />
     </state>"""
    );

    configuration.setParallelCompilationEnabled(false);

    assertEquals(configuration.getParallelCompilationOption(), ParallelCompilationOption.DISABLED);
    assertThat(configuration.getState()).isEqualTo(
     """
     <state>
       <option name="PARALLEL_COMPILATION_OPTION" value="Disabled" />
     </state>"""
    );

  }

  public void testChangedInNewWayParallelCompilationOptionChangesXml() {
    CompilerConfigurationImpl configuration = getConfiguration();

    configuration.setParallelCompilationOption(ParallelCompilationOption.ENABLED);

    assertEquals(configuration.getParallelCompilationOption(), ParallelCompilationOption.ENABLED);
    assertThat(configuration.getState()).isEqualTo(
     """
     <state>
       <option name="PARALLEL_COMPILATION_OPTION" value="Enabled" />
     </state>""");

    configuration.setParallelCompilationOption(ParallelCompilationOption.AUTOMATIC);

    assertEquals(configuration.getParallelCompilationOption(), ParallelCompilationOption.AUTOMATIC);
    assertThat(configuration.getState()).isEqualTo(
     """
     <state>
       <option name="PARALLEL_COMPILATION_OPTION" value="Automatic" />
     </state>""");

    configuration.setParallelCompilationOption(ParallelCompilationOption.DISABLED);

    assertEquals(configuration.getParallelCompilationOption(), ParallelCompilationOption.DISABLED);
    assertThat(configuration.getState()).isEqualTo(
     """
     <state>
       <option name="PARALLEL_COMPILATION_OPTION" value="Disabled" />
     </state>""");
  }

  public void testParallelCompilationOptionMapToBoolean() {
    CompilerConfiguration configuration = getConfiguration();

    configuration.setParallelCompilationOption(ParallelCompilationOption.ENABLED);

    assertTrue(configuration.isParallelCompilationEnabled());

    configuration.setParallelCompilationOption(ParallelCompilationOption.AUTOMATIC);

    assertTrue(configuration.isParallelCompilationEnabled());

    configuration.setParallelCompilationOption(ParallelCompilationOption.DISABLED);

    assertFalse(configuration.isParallelCompilationEnabled());
  }

  private CompilerConfigurationImpl getConfiguration() {
    return (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject);
  }
}
