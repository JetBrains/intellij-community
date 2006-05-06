package com.intellij.execution.junit2.ui;

import com.intellij.execution.Location;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.info.PsiLocator;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class TestsUIUtil {
  public static final Color PASSED_COLOR = new Color(0, 128, 0);
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit2.ui.TestsUIUtil");

  @NonNls private static final String ICONS_ROOT = "/runConfigurations/";

  public static Object getData(final TestProxy testProxy, final String dataId, final JUnitRunningModel model) {
    final Project project = model.getProject();
    if (testProxy == null) return null;
    if (TestProxy.DATA_CONSTANT.equals(dataId)) return testProxy;
    if (DataConstants.NAVIGATABLE.equals(dataId)) return getOpenFileDescriptor(testProxy, model);
    final PsiLocator testInfo = testProxy.getInfo();
    if (DataConstants.PSI_ELEMENT.equals(dataId)) {
      final Location location = testInfo.getLocation(project);
      return location != null ? location.getPsiElement() : null;
    }
    if (Location.LOCATION.equals(dataId)) return testInfo.getLocation(project);
    return null;
  }

  public static Navigatable getOpenFileDescriptor(final TestProxy testProxy, final JUnitRunningModel model) {
    final Project project = model.getProject();
    final JUnitConsoleProperties properties = model.getProperties();
    if (testProxy != null) {
      final Location location = testProxy.getInfo().getLocation(project);
      if (JUnitConsoleProperties.OPEN_FAILURE_LINE.value(properties)) {
        return testProxy.getState().getDescriptor(location);
      }
      final OpenFileDescriptor openFileDescriptor = location == null ? null : location.getOpenFileDescriptor();
      if (openFileDescriptor != null && openFileDescriptor.getFile().isValid()) {
        return openFileDescriptor;
      }
    }
    return null;
  }

  public static Icon loadIcon(@NonNls final String iconName) {
    final Application application = ApplicationManager.getApplication();
    if (application == null || application.isUnitTestMode()) return new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR));
    @NonNls final String fullIconName = ICONS_ROOT + iconName +".png";
    final Icon icon = IconLoader.getIcon(fullIconName);
    LOG.assertTrue(icon != null, fullIconName);
    return icon;
  }
}
