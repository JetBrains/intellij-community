// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.cache.loader;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.cache.client.JpsServerClient;
import org.jetbrains.jps.cache.model.AffectedModule;
import org.jetbrains.jps.cache.model.BuildTargetState;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class JpsCompilationOutputLoaderTest extends BasePlatformTestCase {
  private static final String PRODUCTION = "production";
  private static final String TEST = "test";
  private JpsCompilationOutputLoader compilationOutputLoader;
  private Type myTokenType;
  private Gson myGson;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    compilationOutputLoader = new JpsCompilationOutputLoader(JpsServerClient.getServerClient(""), "/intellij/out/classes");
    myGson = new Gson();
    myTokenType = new TypeToken<Map<String, Map<String, BuildTargetState>>>() {}.getType();
  }

  public void testCurrentModelStateNull() throws IOException {
    List<AffectedModule> affectedModules = compilationOutputLoader.getAffectedModules(null, loadModelFromFile("caseOne.json"), false);
    assertSize(4, affectedModules);
    // 836 production
    assertSize(2, ContainerUtil.filter(affectedModules, module -> module.getType().contains(PRODUCTION)));
    // 407 test
    assertSize(2, ContainerUtil.filter(affectedModules, module -> module.getType().contains(TEST)));
  }

  public void testChangedStatsCollectorModule() throws IOException {
    List<AffectedModule> affectedModules =
      compilationOutputLoader.getAffectedModules(loadModelFromFile("caseOne.json"), loadModelFromFile("caseTwo.json"), false);
    assertSize(1, affectedModules);
    AffectedModule affectedModule = affectedModules.get(0);
    assertEquals("java-production", affectedModule.getType());
    assertEquals("intellij.statsCollector", affectedModule.getName());
  }

  public void testNewType() throws IOException {
    List<AffectedModule> affectedModules = compilationOutputLoader.getAffectedModules(loadModelFromFile("caseTwo.json"),
                                                                                      loadModelFromFile("caseThree.json"), false);
    assertSize(1, affectedModules);
    AffectedModule affectedModule = affectedModules.get(0);
    assertEquals("artifacts", affectedModule.getType());
    assertEquals("intellij.cidr.externalSystem", affectedModule.getName());
  }

  public void testChangedProductionModule() throws IOException {
    List<AffectedModule> affectedModules = compilationOutputLoader.getAffectedModules(loadModelFromFile("caseTwo.json"),
                                                                                      loadModelFromFile("caseFour.json"), false);
    assertSize(1, affectedModules);
    AffectedModule affectedModule = affectedModules.get(0);
    assertEquals(PRODUCTION, affectedModule.getType());
    assertEquals("intellij.cidr.externalSystem", affectedModule.getName());
  }

  public void testNewBuildModule() throws IOException {
    List<AffectedModule> affectedModules = compilationOutputLoader.getAffectedModules(loadModelFromFile("caseFour.json"),
                                                                                      loadModelFromFile("caseFive.json"), false);
    assertSize(1, affectedModules);
    AffectedModule affectedModule = affectedModules.get(0);
    assertEquals("resources-production", affectedModule.getType());
    assertEquals("intellij.sh", affectedModule.getName());
  }

  public void testTargetFolderNotExist() throws IOException {
    List<AffectedModule> affectedModules = compilationOutputLoader.getAffectedModules(loadModelFromFile("caseFour.json"),
                                                                                      loadModelFromFile("caseFive.json"), true);
    assertSize(4, affectedModules);
    List<String> types = ContainerUtil.map(affectedModules, AffectedModule::getType);
    List<String> names = ContainerUtil.map(affectedModules, AffectedModule::getName);
    assertSameElements(types, "java-test", "production", "resources-test", "resources-production");
    assertSameElements(names, "intellij.cidr.externalSystem", "intellij.platform.ssh.integrationTests", "intellij.sh");
  }

  public void testChangedTest() throws IOException {
    List<AffectedModule> affectedModules = compilationOutputLoader.getAffectedModules(loadModelFromFile("caseFive.json"),
                                                                                      loadModelFromFile("caseSix.json"), false);
    assertSize(1, affectedModules);
    AffectedModule affectedModule = affectedModules.get(0);
    assertEquals(TEST, affectedModule.getType());
    assertEquals("intellij.cidr.externalSystem", affectedModule.getName());
  }

  public void testRemoveBuildType() throws IOException {
    compilationOutputLoader.getAffectedModules(loadModelFromFile("removeOne.json"), loadModelFromFile("caseOne.json"), false);
    List<File> oldModulesPaths = compilationOutputLoader.getOldModulesPaths();
    assertSize(1, oldModulesPaths);
    assertEquals("intellij.cidr", oldModulesPaths.get(0).getName());
  }

  public void testRemoveModuleNotExistingInOtherBuildTypes() throws IOException {
    compilationOutputLoader.getAffectedModules(loadModelFromFile("removeTwo.json"), loadModelFromFile("removeOne.json"), false);
    List<File> oldModulesPaths = compilationOutputLoader.getOldModulesPaths();
    assertSize(1, oldModulesPaths);
    assertEquals("intellij.platform.ssh.integrationTests", oldModulesPaths.get(0).getName());
  }

  public void testRemoveModuleExistingInOtherBuildTypes() throws IOException {
    compilationOutputLoader.getAffectedModules(loadModelFromFile("removeThree.json"), loadModelFromFile("removeTwo.json"), false);
    List<File> oldModulesPaths = compilationOutputLoader.getOldModulesPaths();
    assertSize(0, oldModulesPaths);
  }

  private Map<String, Map<String, BuildTargetState>> loadModelFromFile(String fileName) throws IOException {
    try (BufferedReader bufferedReader = new BufferedReader(new FileReader(getTestDataFile(fileName)))) {
      return myGson.fromJson(bufferedReader, myTokenType);
    }
  }

  private static File getTestDataFile(@NotNull String fileName) {
    return new File(PathManagerEx.findFileUnderCommunityHome("jps/jps-builders/testData/cacheLoader"), fileName);
  }
}