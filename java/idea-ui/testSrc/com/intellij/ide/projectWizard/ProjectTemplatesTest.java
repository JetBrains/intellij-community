// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.facet.frameworks.beans.Artifact;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.platform.templates.ArchivedProjectTemplate;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class ProjectTemplatesTest extends TestCase {

  public void testArtifact() throws Exception {
    Artifact artifact = XmlSerializer.deserialize(JDOMUtil.load("""
                                                                    <artifact version="2.2.3" name="Spring Batch"
                                                                              urlPrefix="http://download.jetbrains.com/idea/j2ee_libs/spring/batch/2.2.3/">
                                                                      <item name="spring-batch-core-2.2.3.RELEASE.jar"/>
                                                                      <item name="spring-batch-infrastructure-2.2.3.RELEASE.jar"/>
                                                                    </artifact>\
                                                                  """), Artifact.class);
    assertNotNull(artifact);
    assertEquals(2, artifact.getItems().length);

  }

  public void testTemplate() throws Exception {
    ArchivedProjectTemplate template = new ArchivedProjectTemplate("Display Name", "category") {
      @Override
      protected ModuleType getModuleType() {
        return null;
      }

      @Override
      public <T> T processStream(@NotNull StreamProcessor<T> consumer) {
        return null;
      }

      @Nullable
      @Override
      public String getDescription() {
        return null;
      }
    };

    XmlSerializer.deserializeInto(JDOMUtil.load("""
                                                  <template>
                                                    <input-field default="com.springapp.batch">IJ_BASE_PACKAGE</input-field>
                                                      <icon-path>/icons/spring.png</icon-path>
                                                    <framework>facet:Spring</framework>
                                                    <framework>spring-batch</framework>
                                                    <artifact version="2.2.3" name="Spring Batch"
                                                              urlPrefix="http://download.jetbrains.com/idea/j2ee_libs/spring/batch/2.2.3/">
                                                      <item name="spring-batch-core-2.2.3.RELEASE.jar"/>
                                                      <item name="spring-batch-infrastructure-2.2.3.RELEASE.jar"/>
                                                    </artifact>
                                                    <artifact version="2.2.4" name="Spring Batch"
                                                              urlPrefix="http://download.jetbrains.com/idea/j2ee_libs/spring/batch/2.2.3/">
                                                      <item name="spring-batch-core-2.2.3.RELEASE.jar"/>
                                                      <item name="spring-batch-infrastructure-2.2.3.RELEASE.jar"/>
                                                    </artifact>
                                                  </template>"""), template);
    assertEquals(2, template.getArtifacts().size());
    assertEquals(2, template.getFrameworks().size());
  }
}
