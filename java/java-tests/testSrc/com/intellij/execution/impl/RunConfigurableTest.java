/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.impl;

import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.UnknownConfigurationType;
import com.intellij.execution.junit.JUnitConfigurationType;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Trinity;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.ui.RowsDnDSupport;
import com.intellij.ui.treeStructure.Tree;
import org.jdom.Element;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.execution.impl.RunConfigurable.NodeKind.*;
import static com.intellij.ui.RowsDnDSupport.RefinedDropSupport.Position.*;

/**
 * User: Vassiliy.Kudryashov
 */
public class RunConfigurableTest extends LightIdeaTestCase {
  private static final RunConfigurable.NodeKind[] ORDER = {
    CONFIGURATION_TYPE,//Application
    FOLDER,//1
    CONFIGURATION, CONFIGURATION, CONFIGURATION, CONFIGURATION, CONFIGURATION,
    TEMPORARY_CONFIGURATION, TEMPORARY_CONFIGURATION,
    FOLDER,//2
    TEMPORARY_CONFIGURATION,
    FOLDER,//3
    CONFIGURATION,
    TEMPORARY_CONFIGURATION,
    CONFIGURATION_TYPE,//JUnit
    FOLDER,//4
    CONFIGURATION, CONFIGURATION,
    FOLDER,//5
    CONFIGURATION, CONFIGURATION,
    TEMPORARY_CONFIGURATION,
    UNKNOWN//Defaults
  };
  private MockRunConfigurable myConfigurable;
  private Tree myTree;
  private DefaultMutableTreeNode myRoot;
  private RunConfigurable.MyTreeModel myModel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myConfigurable = new MockRunConfigurable(createRunManager(JDOMUtil.loadDocument(FOLDERS_CONFIGURATION).getRootElement()));
    myTree = myConfigurable.myTree;
    myRoot = myConfigurable.myRoot;
    myModel = myConfigurable.myTreeModel;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myConfigurable != null) myConfigurable.disposeUIResources();
      myConfigurable = null;
      myTree = null;
      myRoot = null;
      myModel = null;
    }
    finally {
      super.tearDown();
    }
  }

  public void testDND() throws Exception {
    doExpand();
    int[] never = {-1, 0, 14, 22, 23, 999};
    for (int i = -1; i < 17; i++) {
      for (int j : never) {
        if ((j == 14 || j == 21) && i == j) {
          continue;
        }
        assertCannot(j,i,ABOVE);
        assertCannot(j,i,INTO);
        assertCannot(j,i,BELOW);
      }
    }
    assertCan(3, 3, BELOW);
    assertCan(3, 3, ABOVE);
    assertCannot(3, 2, BELOW);
    assertCan(3, 2, ABOVE);
    assertCannot(3, 1, BELOW);
    assertCannot(3, 0, BELOW);
    assertCan(2, 14, ABOVE);
    assertCan(1, 14, ABOVE);
    assertCan(1, 11, ABOVE);
    assertCannot(1, 10, ABOVE);
    assertCannot(1, 10, BELOW);
    assertCannot(8, 6, ABOVE);
    assertCan(8, 6, BELOW);
    assertCannot(5, 7, BELOW);
    assertCan(5, 7, ABOVE);
    assertCannot(15, 11, INTO);
    assertCannot(18, 21, ABOVE);
    assertCan(15, 21, ABOVE);

    assertTrue(myModel.isDropInto(myTree, 2, 9));
    assertTrue(myModel.isDropInto(myTree, 2, 1));
    assertTrue(myModel.isDropInto(myTree, 12, 9));
    assertTrue(myModel.isDropInto(myTree, 12, 1));
    assertFalse(myModel.isDropInto(myTree, 999, 9));
    assertFalse(myModel.isDropInto(myTree, 999, 1));
    assertFalse(myModel.isDropInto(myTree, 2, 999));
    assertFalse(myModel.isDropInto(myTree, 2, -1));
  }

  private void doExpand() {
    List<DefaultMutableTreeNode> toExpand = new ArrayList<>();
    RunConfigurable.collectNodesRecursively(myRoot, toExpand, FOLDER);
    assertEquals(5, toExpand.size());
    List<DefaultMutableTreeNode> toExpand2 = new ArrayList<>();
    RunConfigurable.collectNodesRecursively(myRoot, toExpand2, CONFIGURATION_TYPE);
    toExpand.addAll(toExpand2);
    for (DefaultMutableTreeNode node : toExpand) {
      myTree.expandPath(new TreePath(node.getPath()));
    }
    for (int i = 0; i < ORDER.length; i++) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)myTree.getPathForRow(i).getLastPathComponent();
      assertEquals("Row #" + i, RunConfigurable.getKind(node), ORDER[i]);
    }
  }

  private void assertCan(int oldIndex, int newIndex, RowsDnDSupport.RefinedDropSupport.Position position) {
    assertDrop(oldIndex, newIndex, position, true);
  }

  private void assertCannot(int oldIndex, int newIndex, RowsDnDSupport.RefinedDropSupport.Position position) {
    assertDrop(oldIndex, newIndex, position, false);
  }

  private void assertDrop(int oldIndex, int newIndex, RowsDnDSupport.RefinedDropSupport.Position position, boolean canDrop) {
    StringBuilder message = new StringBuilder();
    message.append("(").append(oldIndex).append(")").append(myTree.getPathForRow(oldIndex)).append("->");
    message.append("(").append(newIndex).append(")").append(myTree.getPathForRow(newIndex)).append(position);
    if (canDrop) {
      assertTrue(message.toString(), myModel.canDrop(oldIndex, newIndex, position));
    }
    else {
      assertFalse(message.toString(), myModel.canDrop(oldIndex, newIndex, position));
    }
  }

  public void testMoveUpDown() {
    doExpand();
    checkPositionToMove(0, 1, null);
    checkPositionToMove(2, 1, Trinity.create(2, 3, BELOW));
    checkPositionToMove(2, -1, null);
    checkPositionToMove(14, 1, null);
    checkPositionToMove(14, -1, null);
    checkPositionToMove(15, -1, null);
    checkPositionToMove(16, -1, null);
    checkPositionToMove(3, -1, Trinity.create(3, 2, ABOVE));
    checkPositionToMove(6, 1, Trinity.create(6, 9, BELOW));
    checkPositionToMove(7, 1, Trinity.create(7, 8, BELOW));
    checkPositionToMove(10, -1, Trinity.create(10, 8, BELOW));
    checkPositionToMove(8, 1, Trinity.create(8, 9, BELOW));
    checkPositionToMove(21, -1, Trinity.create(21, 20, BELOW));
    checkPositionToMove(21, 1, null);
    checkPositionToMove(20, 1, Trinity.create(20, 21, ABOVE));
    checkPositionToMove(20, -1, Trinity.create(20, 19, ABOVE));
    checkPositionToMove(19, 1, Trinity.create(19, 20, BELOW));
    checkPositionToMove(19, -1, Trinity.create(19, 17, BELOW));
    checkPositionToMove(17, -1, Trinity.create(17, 16, ABOVE));
    checkPositionToMove(17, 1, Trinity.create(17, 18, BELOW));
  }

  private void checkPositionToMove(int selectedRow,
                                   int direction,
                                   Trinity<Integer, Integer, RowsDnDSupport.RefinedDropSupport.Position> expected) {
    myTree.setSelectionRow(selectedRow);
    assertEquals(expected, myConfigurable.getAvailableDropPosition(direction));
  }

  private static RunManagerImpl createRunManager(Element element) throws InvalidDataException {
    Project project = getProject();
    RunManagerImpl runManager = new RunManagerImpl(project, PropertiesComponent.getInstance(project));
    runManager.initializeConfigurationTypes(new ConfigurationType[]{ApplicationConfigurationType.getInstance(),
      JUnitConfigurationType.getInstance(), UnknownConfigurationType.INSTANCE});
    runManager.loadState(element);
    return runManager;
  }

  private static class MockRunConfigurable extends RunConfigurable {
    private final RunManagerImpl myTestManager;

    private MockRunConfigurable(RunManagerImpl runManager) {
      super(getProject());
      myTestManager = runManager;
      createComponent();
    }

    @Override
    RunManagerImpl getRunManager() {
      return myTestManager;
    }
  }

  /*
00  Application
01   1
02    CodeGenerator
03    Renamer
04    UI
05    AuTest
06    Simples
07    OutAndErr (tmp)
08    C148C_TersePrincess (tmp)
09   2
10    Periods (tmp)
11   3
12    C148E_Porcelain
13    ErrAndOut (tmp)
14  JUnit
15   4
16    All in titled
17    All in titled2
18   5
19    All in titled3
20    All in titled4
21   All in titled5
16  Defaults
   ...
  */
  private static final String FOLDERS_CONFIGURATION = "  <component name=\"RunManager\" selected=\"Application.UI\">\n" +
                                                      "    <configuration default=\"false\" name=\"OutAndErr\" type=\"Application\" factoryName=\"Application\" folderName=\"1\" temporary=\"true\">\n" +
                                                      "      <extension name=\"coverage\" enabled=\"false\" merge=\"false\" sample_coverage=\"true\" runner=\"idea\" />\n" +
                                                      "      <option name=\"MAIN_CLASS_NAME\" value=\"OutAndErr\" />\n" +
                                                      "      <option name=\"VM_PARAMETERS\" value=\"\" />\n" +
                                                      "      <option name=\"PROGRAM_PARAMETERS\" value=\"\" />\n" +
                                                      "      <option name=\"WORKING_DIRECTORY\" value=\"file://$PROJECT_DIR$\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH_ENABLED\" value=\"false\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH\" value=\"\" />\n" +
                                                      "      <option name=\"ENABLE_SWING_INSPECTOR\" value=\"false\" />\n" +
                                                      "      <option name=\"ENV_VARIABLES\" />\n" +
                                                      "      <option name=\"PASS_PARENT_ENVS\" value=\"true\" />\n" +
                                                      "      <module name=\"titled\" />\n" +
                                                      "      <envs />\n" +
                                                      "      <RunnerSettings RunnerId=\"Debug\">\n" +
                                                      "        <option name=\"DEBUG_PORT\" value=\"\" />\n" +
                                                      "        <option name=\"TRANSPORT\" value=\"0\" />\n" +
                                                      "        <option name=\"LOCAL\" value=\"true\" />\n" +
                                                      "      </RunnerSettings>\n" +
                                                      "      <RunnerSettings RunnerId=\"Run\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Debug\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Run\" />\n" +
                                                      "      <method />\n" +
                                                      "    </configuration>\n" +
                                                      "    <configuration default=\"false\" name=\"C148C_TersePrincess\" type=\"Application\" factoryName=\"Application\" folderName=\"1\" temporary=\"true\">\n" +
                                                      "      <extension name=\"coverage\" enabled=\"false\" merge=\"false\" sample_coverage=\"true\" runner=\"idea\" />\n" +
                                                      "      <option name=\"MAIN_CLASS_NAME\" value=\"C148C_TersePrincess\" />\n" +
                                                      "      <option name=\"VM_PARAMETERS\" value=\"\" />\n" +
                                                      "      <option name=\"PROGRAM_PARAMETERS\" value=\"\" />\n" +
                                                      "      <option name=\"WORKING_DIRECTORY\" value=\"file://$PROJECT_DIR$\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH_ENABLED\" value=\"false\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH\" value=\"\" />\n" +
                                                      "      <option name=\"ENABLE_SWING_INSPECTOR\" value=\"false\" />\n" +
                                                      "      <option name=\"ENV_VARIABLES\" />\n" +
                                                      "      <option name=\"PASS_PARENT_ENVS\" value=\"true\" />\n" +
                                                      "      <module name=\"titled\" />\n" +
                                                      "      <envs />\n" +
                                                      "      <RunnerSettings RunnerId=\"Debug\">\n" +
                                                      "        <option name=\"DEBUG_PORT\" value=\"\" />\n" +
                                                      "        <option name=\"TRANSPORT\" value=\"0\" />\n" +
                                                      "        <option name=\"LOCAL\" value=\"true\" />\n" +
                                                      "      </RunnerSettings>\n" +
                                                      "      <RunnerSettings RunnerId=\"Run\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Debug\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Run\" />\n" +
                                                      "      <method />\n" +
                                                      "    </configuration>\n" +
                                                      "    <configuration default=\"false\" name=\"Periods\" type=\"Application\" factoryName=\"Application\" folderName=\"2\" temporary=\"true\">\n" +
                                                      "      <extension name=\"coverage\" enabled=\"false\" merge=\"false\" sample_coverage=\"true\" runner=\"idea\">\n" +
                                                      "        <pattern>\n" +
                                                      "          <option name=\"PATTERN\" value=\"v.*\" />\n" +
                                                      "          <option name=\"ENABLED\" value=\"true\" />\n" +
                                                      "        </pattern>\n" +
                                                      "      </extension>\n" +
                                                      "      <option name=\"MAIN_CLASS_NAME\" value=\"v.Periods\" />\n" +
                                                      "      <option name=\"VM_PARAMETERS\" value=\"\" />\n" +
                                                      "      <option name=\"PROGRAM_PARAMETERS\" value=\"\" />\n" +
                                                      "      <option name=\"WORKING_DIRECTORY\" value=\"file://$PROJECT_DIR$\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH_ENABLED\" value=\"false\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH\" value=\"\" />\n" +
                                                      "      <option name=\"ENABLE_SWING_INSPECTOR\" value=\"false\" />\n" +
                                                      "      <option name=\"ENV_VARIABLES\" />\n" +
                                                      "      <option name=\"PASS_PARENT_ENVS\" value=\"true\" />\n" +
                                                      "      <module name=\"titled\" />\n" +
                                                      "      <envs />\n" +
                                                      "      <RunnerSettings RunnerId=\"Debug\">\n" +
                                                      "        <option name=\"DEBUG_PORT\" value=\"\" />\n" +
                                                      "        <option name=\"TRANSPORT\" value=\"0\" />\n" +
                                                      "        <option name=\"LOCAL\" value=\"true\" />\n" +
                                                      "      </RunnerSettings>\n" +
                                                      "      <RunnerSettings RunnerId=\"Run\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Debug\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Run\" />\n" +
                                                      "      <method />\n" +
                                                      "    </configuration>\n" +
                                                      "    <configuration default=\"false\" name=\"ErrAndOut\" type=\"Application\" factoryName=\"Application\" folderName=\"3\" temporary=\"true\">\n" +
                                                      "      <extension name=\"coverage\" enabled=\"false\" merge=\"false\" sample_coverage=\"true\" runner=\"idea\" />\n" +
                                                      "      <option name=\"MAIN_CLASS_NAME\" value=\"ErrAndOut\" />\n" +
                                                      "      <option name=\"VM_PARAMETERS\" value=\"\" />\n" +
                                                      "      <option name=\"PROGRAM_PARAMETERS\" value=\"\" />\n" +
                                                      "      <option name=\"WORKING_DIRECTORY\" value=\"file://$PROJECT_DIR$\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH_ENABLED\" value=\"false\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH\" value=\"\" />\n" +
                                                      "      <option name=\"ENABLE_SWING_INSPECTOR\" value=\"false\" />\n" +
                                                      "      <option name=\"ENV_VARIABLES\" />\n" +
                                                      "      <option name=\"PASS_PARENT_ENVS\" value=\"true\" />\n" +
                                                      "      <module name=\"titled\" />\n" +
                                                      "      <envs />\n" +
                                                      "      <RunnerSettings RunnerId=\"Debug\">\n" +
                                                      "        <option name=\"DEBUG_PORT\" value=\"\" />\n" +
                                                      "        <option name=\"TRANSPORT\" value=\"0\" />\n" +
                                                      "        <option name=\"LOCAL\" value=\"true\" />\n" +
                                                      "      </RunnerSettings>\n" +
                                                      "      <RunnerSettings RunnerId=\"Run\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Debug\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Run\" />\n" +
                                                      "      <method />\n" +
                                                      "    </configuration>\n" +
                                                      "    <configuration default=\"true\" type=\"MavenRunConfiguration\" factoryName=\"Maven\">\n" +
                                                      "      <MavenSettings>\n" +
                                                      "        <option name=\"myGeneralSettings\" />\n" +
                                                      "        <option name=\"myRunnerSettings\" />\n" +
                                                      "        <option name=\"myRunnerParameters\">\n" +
                                                      "          <MavenRunnerParameters>\n" +
                                                      "            <option name=\"profiles\">\n" +
                                                      "              <set />\n" +
                                                      "            </option>\n" +
                                                      "            <option name=\"goals\">\n" +
                                                      "              <list />\n" +
                                                      "            </option>\n" +
                                                      "            <option name=\"profilesMap\">\n" +
                                                      "              <map />\n" +
                                                      "            </option>\n" +
                                                      "            <option name=\"workingDirPath\" value=\"\" />\n" +
                                                      "          </MavenRunnerParameters>\n" +
                                                      "        </option>\n" +
                                                      "      </MavenSettings>\n" +
                                                      "      <method />\n" +
                                                      "    </configuration>\n" +
                                                      "    <configuration default=\"true\" type=\"JUnit\" factoryName=\"JUnit\">\n" +
                                                      "      <extension name=\"coverage\" enabled=\"false\" merge=\"false\" sample_coverage=\"true\" runner=\"idea\" />\n" +
                                                      "      <module name=\"\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH_ENABLED\" value=\"false\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH\" value=\"\" />\n" +
                                                      "      <option name=\"PACKAGE_NAME\" />\n" +
                                                      "      <option name=\"MAIN_CLASS_NAME\" value=\"\" />\n" +
                                                      "      <option name=\"METHOD_NAME\" value=\"\" />\n" +
                                                      "      <option name=\"TEST_OBJECT\" value=\"class\" />\n" +
                                                      "      <option name=\"VM_PARAMETERS\" value=\"-ea\" />\n" +
                                                      "      <option name=\"PARAMETERS\" value=\"\" />\n" +
                                                      "      <option name=\"WORKING_DIRECTORY\" value=\"file://$PROJECT_DIR$\" />\n" +
                                                      "      <option name=\"ENV_VARIABLES\" />\n" +
                                                      "      <option name=\"PASS_PARENT_ENVS\" value=\"true\" />\n" +
                                                      "      <option name=\"TEST_SEARCH_SCOPE\">\n" +
                                                      "        <value defaultName=\"moduleWithDependencies\" />\n" +
                                                      "      </option>\n" +
                                                      "      <envs />\n" +
                                                      "      <patterns />\n" +
                                                      "      <method>\n" +
                                                      "        <option name=\"Make\" enabled=\"false\" />\n" +
                                                      "      </method>\n" +
                                                      "    </configuration>\n" +
                                                      "    <configuration default=\"true\" type=\"Application\" factoryName=\"Application\">\n" +
                                                      "      <extension name=\"coverage\" enabled=\"false\" merge=\"false\" sample_coverage=\"true\" runner=\"idea\" />\n" +
                                                      "      <option name=\"MAIN_CLASS_NAME\" value=\"\" />\n" +
                                                      "      <option name=\"VM_PARAMETERS\" value=\"\" />\n" +
                                                      "      <option name=\"PROGRAM_PARAMETERS\" value=\"\" />\n" +
                                                      "      <option name=\"WORKING_DIRECTORY\" value=\"file://$PROJECT_DIR$\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH_ENABLED\" value=\"false\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH\" value=\"\" />\n" +
                                                      "      <option name=\"ENABLE_SWING_INSPECTOR\" value=\"false\" />\n" +
                                                      "      <option name=\"ENV_VARIABLES\" />\n" +
                                                      "      <option name=\"PASS_PARENT_ENVS\" value=\"true\" />\n" +
                                                      "      <module name=\"\" />\n" +
                                                      "      <envs />\n" +
                                                      "      <method />\n" +
                                                      "    </configuration>\n" +
                                                      "    <configuration default=\"false\" name=\"CodeGenerator\" type=\"Application\" factoryName=\"Application\" folderName=\"1\">\n" +
                                                      "      <extension name=\"coverage\" enabled=\"false\" merge=\"false\" sample_coverage=\"true\" runner=\"idea\">\n" +
                                                      "        <pattern>\n" +
                                                      "          <option name=\"PATTERN\" value=\"codegen.*\" />\n" +
                                                      "          <option name=\"ENABLED\" value=\"true\" />\n" +
                                                      "        </pattern>\n" +
                                                      "      </extension>\n" +
                                                      "      <option name=\"MAIN_CLASS_NAME\" value=\"codegen.CodeGenerator\" />\n" +
                                                      "      <option name=\"VM_PARAMETERS\" value=\"\" />\n" +
                                                      "      <option name=\"PROGRAM_PARAMETERS\" value=\"\" />\n" +
                                                      "      <option name=\"WORKING_DIRECTORY\" value=\"file://$PROJECT_DIR$\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH_ENABLED\" value=\"false\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH\" value=\"\" />\n" +
                                                      "      <option name=\"ENABLE_SWING_INSPECTOR\" value=\"false\" />\n" +
                                                      "      <option name=\"ENV_VARIABLES\" />\n" +
                                                      "      <option name=\"PASS_PARENT_ENVS\" value=\"true\" />\n" +
                                                      "      <module name=\"titled\" />\n" +
                                                      "      <envs />\n" +
                                                      "      <RunnerSettings RunnerId=\"Run\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Run\" />\n" +
                                                      "      <method />\n" +
                                                      "    </configuration>\n" +
                                                      "    <configuration default=\"false\" name=\"Renamer\" type=\"Application\" factoryName=\"Application\" folderName=\"1\">\n" +
                                                      "      <extension name=\"coverage\" enabled=\"false\" merge=\"false\" sample_coverage=\"true\" runner=\"idea\" />\n" +
                                                      "      <option name=\"MAIN_CLASS_NAME\" value=\"Renamer\" />\n" +
                                                      "      <option name=\"VM_PARAMETERS\" value=\"\" />\n" +
                                                      "      <option name=\"PROGRAM_PARAMETERS\" value=\"\" />\n" +
                                                      "      <option name=\"WORKING_DIRECTORY\" value=\"file://$PROJECT_DIR$\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH_ENABLED\" value=\"false\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH\" value=\"\" />\n" +
                                                      "      <option name=\"ENABLE_SWING_INSPECTOR\" value=\"false\" />\n" +
                                                      "      <option name=\"ENV_VARIABLES\" />\n" +
                                                      "      <option name=\"PASS_PARENT_ENVS\" value=\"true\" />\n" +
                                                      "      <module name=\"titled\" />\n" +
                                                      "      <envs />\n" +
                                                      "      <RunnerSettings RunnerId=\"Debug\">\n" +
                                                      "        <option name=\"DEBUG_PORT\" value=\"\" />\n" +
                                                      "        <option name=\"TRANSPORT\" value=\"0\" />\n" +
                                                      "        <option name=\"LOCAL\" value=\"true\" />\n" +
                                                      "      </RunnerSettings>\n" +
                                                      "      <RunnerSettings RunnerId=\"Run\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Debug\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Run\" />\n" +
                                                      "      <method />\n" +
                                                      "    </configuration>\n" +
                                                      "    <configuration default=\"false\" name=\"UI\" type=\"Application\" factoryName=\"Application\" folderName=\"1\">\n" +
                                                      "      <extension name=\"coverage\" enabled=\"false\" merge=\"false\" sample_coverage=\"true\" runner=\"idea\" />\n" +
                                                      "      <option name=\"MAIN_CLASS_NAME\" value=\"UI\" />\n" +
                                                      "      <option name=\"VM_PARAMETERS\" value=\"\" />\n" +
                                                      "      <option name=\"PROGRAM_PARAMETERS\" value=\"\" />\n" +
                                                      "      <option name=\"WORKING_DIRECTORY\" value=\"file://$PROJECT_DIR$\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH_ENABLED\" value=\"false\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH\" value=\"\" />\n" +
                                                      "      <option name=\"ENABLE_SWING_INSPECTOR\" value=\"false\" />\n" +
                                                      "      <option name=\"ENV_VARIABLES\" />\n" +
                                                      "      <option name=\"PASS_PARENT_ENVS\" value=\"true\" />\n" +
                                                      "      <module name=\"titled\" />\n" +
                                                      "      <envs />\n" +
                                                      "      <RunnerSettings RunnerId=\"Debug\">\n" +
                                                      "        <option name=\"DEBUG_PORT\" value=\"\" />\n" +
                                                      "        <option name=\"TRANSPORT\" value=\"0\" />\n" +
                                                      "        <option name=\"LOCAL\" value=\"true\" />\n" +
                                                      "      </RunnerSettings>\n" +
                                                      "      <RunnerSettings RunnerId=\"Run\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Debug\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Run\" />\n" +
                                                      "      <method />\n" +
                                                      "    </configuration>\n" +
                                                      "    <configuration default=\"false\" name=\"AuTest\" type=\"Application\" factoryName=\"Application\" folderName=\"1\">\n" +
                                                      "      <extension name=\"coverage\" enabled=\"false\" merge=\"false\" sample_coverage=\"true\" runner=\"idea\">\n" +
                                                      "        <pattern>\n" +
                                                      "          <option name=\"PATTERN\" value=\"au.*\" />\n" +
                                                      "          <option name=\"ENABLED\" value=\"true\" />\n" +
                                                      "        </pattern>\n" +
                                                      "      </extension>\n" +
                                                      "      <option name=\"MAIN_CLASS_NAME\" value=\"au.AuTest\" />\n" +
                                                      "      <option name=\"VM_PARAMETERS\" value=\"\" />\n" +
                                                      "      <option name=\"PROGRAM_PARAMETERS\" value=\"\" />\n" +
                                                      "      <option name=\"WORKING_DIRECTORY\" value=\"file://$PROJECT_DIR$\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH_ENABLED\" value=\"false\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH\" value=\"\" />\n" +
                                                      "      <option name=\"ENABLE_SWING_INSPECTOR\" value=\"false\" />\n" +
                                                      "      <option name=\"ENV_VARIABLES\" />\n" +
                                                      "      <option name=\"PASS_PARENT_ENVS\" value=\"true\" />\n" +
                                                      "      <module name=\"titled\" />\n" +
                                                      "      <envs />\n" +
                                                      "      <RunnerSettings RunnerId=\"Debug\">\n" +
                                                      "        <option name=\"DEBUG_PORT\" value=\"\" />\n" +
                                                      "        <option name=\"TRANSPORT\" value=\"0\" />\n" +
                                                      "        <option name=\"LOCAL\" value=\"true\" />\n" +
                                                      "      </RunnerSettings>\n" +
                                                      "      <RunnerSettings RunnerId=\"Run\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Debug\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Run\" />\n" +
                                                      "      <method />\n" +
                                                      "    </configuration>\n" +
                                                      "    <configuration default=\"false\" name=\"Simples\" type=\"Application\" factoryName=\"Application\" folderName=\"1\">\n" +
                                                      "      <extension name=\"coverage\" enabled=\"false\" merge=\"false\" sample_coverage=\"true\" runner=\"idea\" />\n" +
                                                      "      <option name=\"MAIN_CLASS_NAME\" value=\"Simples\" />\n" +
                                                      "      <option name=\"VM_PARAMETERS\" value=\"\" />\n" +
                                                      "      <option name=\"PROGRAM_PARAMETERS\" value=\"\" />\n" +
                                                      "      <option name=\"WORKING_DIRECTORY\" value=\"file://$PROJECT_DIR$\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH_ENABLED\" value=\"false\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH\" value=\"\" />\n" +
                                                      "      <option name=\"ENABLE_SWING_INSPECTOR\" value=\"false\" />\n" +
                                                      "      <option name=\"ENV_VARIABLES\" />\n" +
                                                      "      <option name=\"PASS_PARENT_ENVS\" value=\"true\" />\n" +
                                                      "      <module name=\"titled\" />\n" +
                                                      "      <envs />\n" +
                                                      "      <RunnerSettings RunnerId=\"Debug\">\n" +
                                                      "        <option name=\"DEBUG_PORT\" value=\"\" />\n" +
                                                      "        <option name=\"TRANSPORT\" value=\"0\" />\n" +
                                                      "        <option name=\"LOCAL\" value=\"true\" />\n" +
                                                      "      </RunnerSettings>\n" +
                                                      "      <RunnerSettings RunnerId=\"Run\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Debug\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Run\" />\n" +
                                                      "      <method />\n" +
                                                      "    </configuration>\n" +
                                                      "    <configuration default=\"false\" name=\"C148E_Porcelain\" type=\"Application\" factoryName=\"Application\" folderName=\"3\">\n" +
                                                      "      <extension name=\"coverage\" enabled=\"false\" merge=\"false\" sample_coverage=\"true\" runner=\"idea\" />\n" +
                                                      "      <option name=\"MAIN_CLASS_NAME\" value=\"C148E_Porcelain\" />\n" +
                                                      "      <option name=\"VM_PARAMETERS\" value=\"\" />\n" +
                                                      "      <option name=\"PROGRAM_PARAMETERS\" value=\"\" />\n" +
                                                      "      <option name=\"WORKING_DIRECTORY\" value=\"file://$PROJECT_DIR$\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH_ENABLED\" value=\"false\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH\" value=\"\" />\n" +
                                                      "      <option name=\"ENABLE_SWING_INSPECTOR\" value=\"false\" />\n" +
                                                      "      <option name=\"ENV_VARIABLES\" />\n" +
                                                      "      <option name=\"PASS_PARENT_ENVS\" value=\"true\" />\n" +
                                                      "      <module name=\"titled\" />\n" +
                                                      "      <envs />\n" +
                                                      "      <RunnerSettings RunnerId=\"Debug\">\n" +
                                                      "        <option name=\"DEBUG_PORT\" value=\"\" />\n" +
                                                      "        <option name=\"TRANSPORT\" value=\"0\" />\n" +
                                                      "        <option name=\"LOCAL\" value=\"true\" />\n" +
                                                      "      </RunnerSettings>\n" +
                                                      "      <RunnerSettings RunnerId=\"Run\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Debug\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Run\" />\n" +
                                                      "      <method />\n" +
                                                      "    </configuration>\n" +
                                                      "    <configuration default=\"false\" name=\"All in titled\" type=\"JUnit\" factoryName=\"JUnit\" folderName=\"4\">\n" +
                                                      "      <output_file path=\"C:/tst.txt\" is_save=\"true\" />\n" +
                                                      "      <extension name=\"coverage\" enabled=\"false\" merge=\"false\" sample_coverage=\"true\" runner=\"idea\" />\n" +
                                                      "      <module name=\"titled\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH_ENABLED\" value=\"false\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH\" value=\"\" />\n" +
                                                      "      <option name=\"PACKAGE_NAME\" value=\"\" />\n" +
                                                      "      <option name=\"MAIN_CLASS_NAME\" value=\"\" />\n" +
                                                      "      <option name=\"METHOD_NAME\" value=\"\" />\n" +
                                                      "      <option name=\"TEST_OBJECT\" value=\"package\" />\n" +
                                                      "      <option name=\"VM_PARAMETERS\" value=\"-ea\" />\n" +
                                                      "      <option name=\"PARAMETERS\" value=\"\" />\n" +
                                                      "      <option name=\"WORKING_DIRECTORY\" value=\"file://$PROJECT_DIR$\" />\n" +
                                                      "      <option name=\"ENV_VARIABLES\" />\n" +
                                                      "      <option name=\"PASS_PARENT_ENVS\" value=\"true\" />\n" +
                                                      "      <option name=\"TEST_SEARCH_SCOPE\">\n" +
                                                      "        <value defaultName=\"moduleWithDependencies\" />\n" +
                                                      "      </option>\n" +
                                                      "      <envs />\n" +
                                                      "      <patterns />\n" +
                                                      "      <RunnerSettings RunnerId=\"Debug\">\n" +
                                                      "        <option name=\"DEBUG_PORT\" value=\"\" />\n" +
                                                      "        <option name=\"TRANSPORT\" value=\"0\" />\n" +
                                                      "        <option name=\"LOCAL\" value=\"true\" />\n" +
                                                      "      </RunnerSettings>\n" +
                                                      "      <RunnerSettings RunnerId=\"Run\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Debug\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Run\" />\n" +
                                                      "      <method>\n" +
                                                      "        <option name=\"Make\" enabled=\"true\" />\n" +
                                                      "      </method>\n" +
                                                      "    </configuration>\n" +
                                                      "    <configuration default=\"false\" name=\"All in titled2\" type=\"JUnit\" factoryName=\"JUnit\" folderName=\"4\">\n" +
                                                      "      <output_file path=\"C:/tst.txt\" is_save=\"true\" />\n" +
                                                      "      <extension name=\"coverage\" enabled=\"false\" merge=\"false\" sample_coverage=\"true\" runner=\"idea\" />\n" +
                                                      "      <module name=\"titled\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH_ENABLED\" value=\"false\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH\" value=\"\" />\n" +
                                                      "      <option name=\"PACKAGE_NAME\" value=\"\" />\n" +
                                                      "      <option name=\"MAIN_CLASS_NAME\" value=\"\" />\n" +
                                                      "      <option name=\"METHOD_NAME\" value=\"\" />\n" +
                                                      "      <option name=\"TEST_OBJECT\" value=\"package\" />\n" +
                                                      "      <option name=\"VM_PARAMETERS\" value=\"-ea\" />\n" +
                                                      "      <option name=\"PARAMETERS\" value=\"\" />\n" +
                                                      "      <option name=\"WORKING_DIRECTORY\" value=\"file://$PROJECT_DIR$\" />\n" +
                                                      "      <option name=\"ENV_VARIABLES\" />\n" +
                                                      "      <option name=\"PASS_PARENT_ENVS\" value=\"true\" />\n" +
                                                      "      <option name=\"TEST_SEARCH_SCOPE\">\n" +
                                                      "        <value defaultName=\"moduleWithDependencies\" />\n" +
                                                      "      </option>\n" +
                                                      "      <envs />\n" +
                                                      "      <patterns />\n" +
                                                      "      <RunnerSettings RunnerId=\"Debug\">\n" +
                                                      "        <option name=\"DEBUG_PORT\" value=\"\" />\n" +
                                                      "        <option name=\"TRANSPORT\" value=\"0\" />\n" +
                                                      "        <option name=\"LOCAL\" value=\"true\" />\n" +
                                                      "      </RunnerSettings>\n" +
                                                      "      <RunnerSettings RunnerId=\"Run\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Debug\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Run\" />\n" +
                                                      "      <method>\n" +
                                                      "        <option name=\"Make\" enabled=\"true\" />\n" +
                                                      "      </method>\n" +
                                                      "    </configuration>\n" +
                                                      "    <configuration default=\"false\" name=\"All in titled3\" type=\"JUnit\" factoryName=\"JUnit\" folderName=\"5\">\n" +
                                                      "      <output_file path=\"C:/tst.txt\" is_save=\"true\" />\n" +
                                                      "      <extension name=\"coverage\" enabled=\"false\" merge=\"false\" sample_coverage=\"true\" runner=\"idea\" />\n" +
                                                      "      <module name=\"titled\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH_ENABLED\" value=\"false\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH\" value=\"\" />\n" +
                                                      "      <option name=\"PACKAGE_NAME\" value=\"\" />\n" +
                                                      "      <option name=\"MAIN_CLASS_NAME\" value=\"\" />\n" +
                                                      "      <option name=\"METHOD_NAME\" value=\"\" />\n" +
                                                      "      <option name=\"TEST_OBJECT\" value=\"package\" />\n" +
                                                      "      <option name=\"VM_PARAMETERS\" value=\"-ea\" />\n" +
                                                      "      <option name=\"PARAMETERS\" value=\"\" />\n" +
                                                      "      <option name=\"WORKING_DIRECTORY\" value=\"file://$PROJECT_DIR$\" />\n" +
                                                      "      <option name=\"ENV_VARIABLES\" />\n" +
                                                      "      <option name=\"PASS_PARENT_ENVS\" value=\"true\" />\n" +
                                                      "      <option name=\"TEST_SEARCH_SCOPE\">\n" +
                                                      "        <value defaultName=\"moduleWithDependencies\" />\n" +
                                                      "      </option>\n" +
                                                      "      <envs />\n" +
                                                      "      <patterns />\n" +
                                                      "      <RunnerSettings RunnerId=\"Debug\">\n" +
                                                      "        <option name=\"DEBUG_PORT\" value=\"\" />\n" +
                                                      "        <option name=\"TRANSPORT\" value=\"0\" />\n" +
                                                      "        <option name=\"LOCAL\" value=\"true\" />\n" +
                                                      "      </RunnerSettings>\n" +
                                                      "      <RunnerSettings RunnerId=\"Run\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Debug\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Run\" />\n" +
                                                      "      <method>\n" +
                                                      "        <option name=\"Make\" enabled=\"true\" />\n" +
                                                      "      </method>\n" +
                                                      "    </configuration>\n" +
                                                      "    <configuration default=\"false\" name=\"All in titled4\" type=\"JUnit\" factoryName=\"JUnit\" folderName=\"5\">\n" +
                                                      "      <output_file path=\"C:/tst.txt\" is_save=\"true\" />\n" +
                                                      "      <extension name=\"coverage\" enabled=\"false\" merge=\"false\" sample_coverage=\"true\" runner=\"idea\" />\n" +
                                                      "      <module name=\"titled\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH_ENABLED\" value=\"false\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH\" value=\"\" />\n" +
                                                      "      <option name=\"PACKAGE_NAME\" value=\"\" />\n" +
                                                      "      <option name=\"MAIN_CLASS_NAME\" value=\"\" />\n" +
                                                      "      <option name=\"METHOD_NAME\" value=\"\" />\n" +
                                                      "      <option name=\"TEST_OBJECT\" value=\"package\" />\n" +
                                                      "      <option name=\"VM_PARAMETERS\" value=\"-ea\" />\n" +
                                                      "      <option name=\"PARAMETERS\" value=\"\" />\n" +
                                                      "      <option name=\"WORKING_DIRECTORY\" value=\"file://$PROJECT_DIR$\" />\n" +
                                                      "      <option name=\"ENV_VARIABLES\" />\n" +
                                                      "      <option name=\"PASS_PARENT_ENVS\" value=\"true\" />\n" +
                                                      "      <option name=\"TEST_SEARCH_SCOPE\">\n" +
                                                      "        <value defaultName=\"moduleWithDependencies\" />\n" +
                                                      "      </option>\n" +
                                                      "      <envs />\n" +
                                                      "      <patterns />\n" +
                                                      "      <RunnerSettings RunnerId=\"Debug\">\n" +
                                                      "        <option name=\"DEBUG_PORT\" value=\"\" />\n" +
                                                      "        <option name=\"TRANSPORT\" value=\"0\" />\n" +
                                                      "        <option name=\"LOCAL\" value=\"true\" />\n" +
                                                      "      </RunnerSettings>\n" +
                                                      "      <RunnerSettings RunnerId=\"Run\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Debug\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Run\" />\n" +
                                                      "      <method>\n" +
                                                      "        <option name=\"Make\" enabled=\"true\" />\n" +
                                                      "      </method>\n" +
                                                      "    </configuration>\n" +
                                                      "    <configuration default=\"false\" name=\"All in titled5\" type=\"JUnit\" factoryName=\"JUnit\" temporary=\"true\">\n" +
                                                      "      <output_file path=\"C:/tst.txt\" is_save=\"true\" />\n" +
                                                      "      <extension name=\"coverage\" enabled=\"false\" merge=\"false\" sample_coverage=\"true\" runner=\"idea\" />\n" +
                                                      "      <module name=\"titled\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH_ENABLED\" value=\"false\" />\n" +
                                                      "      <option name=\"ALTERNATIVE_JRE_PATH\" value=\"\" />\n" +
                                                      "      <option name=\"PACKAGE_NAME\" value=\"\" />\n" +
                                                      "      <option name=\"MAIN_CLASS_NAME\" value=\"\" />\n" +
                                                      "      <option name=\"METHOD_NAME\" value=\"\" />\n" +
                                                      "      <option name=\"TEST_OBJECT\" value=\"package\" />\n" +
                                                      "      <option name=\"VM_PARAMETERS\" value=\"-ea\" />\n" +
                                                      "      <option name=\"PARAMETERS\" value=\"\" />\n" +
                                                      "      <option name=\"WORKING_DIRECTORY\" value=\"file://$PROJECT_DIR$\" />\n" +
                                                      "      <option name=\"ENV_VARIABLES\" />\n" +
                                                      "      <option name=\"PASS_PARENT_ENVS\" value=\"true\" />\n" +
                                                      "      <option name=\"TEST_SEARCH_SCOPE\">\n" +
                                                      "        <value defaultName=\"moduleWithDependencies\" />\n" +
                                                      "      </option>\n" +
                                                      "      <envs />\n" +
                                                      "      <patterns />\n" +
                                                      "      <RunnerSettings RunnerId=\"Debug\">\n" +
                                                      "        <option name=\"DEBUG_PORT\" value=\"\" />\n" +
                                                      "        <option name=\"TRANSPORT\" value=\"0\" />\n" +
                                                      "        <option name=\"LOCAL\" value=\"true\" />\n" +
                                                      "      </RunnerSettings>\n" +
                                                      "      <RunnerSettings RunnerId=\"Run\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Debug\" />\n" +
                                                      "      <ConfigurationWrapper RunnerId=\"Run\" />\n" +
                                                      "      <method>\n" +
                                                      "        <option name=\"Make\" enabled=\"true\" />\n" +
                                                      "      </method>\n" +
                                                      "    </configuration>\n" +
                                                      "    <list size=\"16\">\n" +
                                                      "      <item index=\"0\" class=\"java.lang.String\" itemvalue=\"Application.CodeGenerator\" />\n" +
                                                      "      <item index=\"1\" class=\"java.lang.String\" itemvalue=\"Application.Renamer\" />\n" +
                                                      "      <item index=\"2\" class=\"java.lang.String\" itemvalue=\"Application.UI\" />\n" +
                                                      "      <item index=\"3\" class=\"java.lang.String\" itemvalue=\"Application.AuTest\" />\n" +
                                                      "      <item index=\"4\" class=\"java.lang.String\" itemvalue=\"Application.Simples\" />\n" +
                                                      "      <item index=\"5\" class=\"java.lang.String\" itemvalue=\"Application.OutAndErr\" />\n" +
                                                      "      <item index=\"6\" class=\"java.lang.String\" itemvalue=\"Application.C148C_TersePrincess\" />\n" +
                                                      "      <item index=\"7\" class=\"java.lang.String\" itemvalue=\"Application.Periods\" />\n" +
                                                      "      <item index=\"8\" class=\"java.lang.String\" itemvalue=\"Application.C148E_Porcelain\" />\n" +
                                                      "      <item index=\"9\" class=\"java.lang.String\" itemvalue=\"Application.ErrAndOut\" />\n" +
                                                      "      <item index=\"10\" class=\"java.lang.String\" itemvalue=\"JUnit.All in titled\" />\n" +
                                                      "      <item index=\"11\" class=\"java.lang.String\" itemvalue=\"JUnit.All in titled2\" />\n" +
                                                      "      <item index=\"12\" class=\"java.lang.String\" itemvalue=\"JUnit.All in titled3\" />\n" +
                                                      "      <item index=\"13\" class=\"java.lang.String\" itemvalue=\"JUnit.All in titled4\" />\n" +
                                                      "      <item index=\"14\" class=\"java.lang.String\" itemvalue=\"JUnit.All in titled5\" />\n" +
                                                      "    </list>\n" +
                                                      "    <recent_temporary>\n" +
                                                      "      <list size=\"4\">\n" +
                                                      "        <item index=\"0\" class=\"java.lang.String\" itemvalue=\"Application.ErrAndOut\" />\n" +
                                                      "        <item index=\"1\" class=\"java.lang.String\" itemvalue=\"Application.Periods\" />\n" +
                                                      "        <item index=\"2\" class=\"java.lang.String\" itemvalue=\"Application.OutAndErr\" />\n" +
                                                      "        <item index=\"3\" class=\"java.lang.String\" itemvalue=\"Application.C148C_TersePrincess\" />\n" +
                                                      "      </list>\n" +
                                                      "    </recent_temporary>\n" +
                                                      "    <configuration name=\"&lt;template&gt;\" type=\"WebApp\" default=\"true\" selected=\"false\">\n" +
                                                      "      <Host>localhost</Host>\n" +
                                                      "      <Port>5050</Port>\n" +
                                                      "    </configuration>\n" +
                                                      "  </component>\n";
}
