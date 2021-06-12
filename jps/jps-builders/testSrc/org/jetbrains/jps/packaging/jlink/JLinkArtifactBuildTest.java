// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.packaging.jlink;

import com.intellij.util.EnvironmentUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuilderTestCase;
import org.jetbrains.jps.incremental.artifacts.LayoutElementTestUtil.LayoutElementCreator;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactService;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.packaging.jlink.JpsJLinkProperties.CompressionLevel;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.jetbrains.jps.incremental.artifacts.LayoutElementTestUtil.root;

public class JLinkArtifactBuildTest extends ArtifactBuilderTestCase {

  public void testImageBuild() {
    String firstModuleDescriptor = createFile("first/src/module-info.java", "module first { requires second; }");
    String firstModuleName = "first";
    JpsModule firstModule = addModule(firstModuleName, PathUtil.getParentPath(firstModuleDescriptor));
    createFile("first/src/pack1/Foo.java",
               "package pack1; import pack2.Bar; public class Foo { public void foo() { System.out.println(new Bar().bar()); } }");

    String secondModuleDescriptor = createFile("second/src/module-info.java", "module second { exports pack2; }");
    String secondModuleName = "second";
    JpsModule second = addModule(secondModuleName, PathUtil.getParentPath(secondModuleDescriptor));
    createFile("second/src/pack2/Bar.java", "package pack2; public class Bar { public String bar() { return \"bar\"; } }");

    firstModule.getDependenciesList().addModuleDependency(second);

    LayoutElementCreator root = root()
      .dir(firstModuleName).module(firstModule).end()
      .dir(secondModuleName).module(second).end();
    String artifactName = "jlink";
    JpsArtifact artifact = JpsArtifactService.getInstance().addArtifact(myProject, artifactName, root.buildElement(),
                                                                        JpsJLinkArtifactType.INSTANCE,
                                                                        new JpsJLinkProperties(CompressionLevel.SECOND, true));
    artifact.setOutputPath(getAbsolutePath("out/artifacts/" + artifactName));
    buildArtifacts(artifact);

    String artifactOutputPath = artifact.getOutputFilePath();
    assertTrue(Files.exists(Paths.get(artifactOutputPath, firstModuleName)));
    assertTrue(Files.exists(Paths.get(artifactOutputPath, secondModuleName)));
    assertTrue(Files.exists(Paths.get(artifactOutputPath, JLinkArtifactBuildTaskProvider.IMAGE_DIR_NAME)));
  }

  @Override
  protected JpsSdk<JpsDummyElement> addJdk(String name) {
    // reused JDK_11 property on TC server to get JDK as default JDK doesn't contains jlink tool in home directory
    String jdkPath = EnvironmentUtil.getValue("JDK_11");
    if (jdkPath == null) return super.addJdk(name);
    JpsTypedLibrary<JpsSdk<JpsDummyElement>> newJdk = myModel.getGlobal().addSdk(name, jdkPath, "11", JpsJavaSdkType.INSTANCE);
    return newJdk.getProperties();
  }
}
