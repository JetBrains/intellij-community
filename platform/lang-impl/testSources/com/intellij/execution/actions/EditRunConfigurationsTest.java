// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.PlatformTestCase;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.Arrays;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

public class EditRunConfigurationsTest extends PlatformTestCase {
  @SuppressWarnings("OptionalGetWithoutIsPresent")
  public void testAvailableForDefaultProject() {
    final MouseEvent mouseEvent = new MouseEvent(new JPanel(), -1, 0, 0, 0, 0, 1, true);
    final AnActionEvent event = new AnActionEvent(mouseEvent, new MapDataContext(), ActionPlaces.UNKNOWN, new Presentation(), ActionManager.getInstance(), 0);
    AnAction editRunConfigurationAction = Arrays.stream(((ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_WELCOME_SCREEN_CONFIGURE)).getChildren(event))
      .filter(action -> action instanceof EditRunConfigurationTemplatesForNewProjectsAction)
      .findFirst()
      .get();

    editRunConfigurationAction.update(event);
    Presentation presentation = event.getPresentation();
    assertThat(presentation.isEnabled()).isTrue();
    assertThat(presentation.isVisible()).isTrue();
  }
}
