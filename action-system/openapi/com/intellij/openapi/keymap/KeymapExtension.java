/*
 * Created by IntelliJ IDEA.
 * User: Vladislav.Kaznacheev
 * Date: Jul 4, 2007
 * Time: 3:59:52 PM
 */
package com.intellij.openapi.keymap;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.List;
import java.util.Map;

public interface KeymapExtension {
  @NonNls ExtensionPointName<KeymapExtension> EXTENSION_POINT_NAME = new ExtensionPointName<KeymapExtension>("com.intellij.keymapExtension");

  String getGroupName ();

  Icon getIcon ();

  Icon getOpenIcon ();

  String getSubgroupName(Object key, Project project);

  Map<Object, List<String>> createSubGroups(Condition<AnAction> filtered, Project project);
}