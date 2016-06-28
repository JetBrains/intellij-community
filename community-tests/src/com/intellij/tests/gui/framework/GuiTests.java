/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.tests.gui.framework;

import com.google.common.collect.Lists;
import com.intellij.diagnostic.AbstractMessage;
import com.intellij.diagnostic.MessagePool;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.tests.gui.fixtures.IdeFrameFixture;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.list.ListPopupModel;
import org.fest.swing.core.BasicRobot;
import org.fest.swing.core.ComponentFinder;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.exception.WaitTimedOutError;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Pause;
import org.fest.swing.timing.Timeout;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Joiner.on;
import static com.google.common.io.Files.createTempDir;
import static com.intellij.openapi.projectRoots.JdkUtil.checkForJdk;
import static com.intellij.openapi.util.io.FileUtil.toCanonicalPath;
import static com.intellij.openapi.util.io.FileUtilRt.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.fest.swing.finder.WindowFinder.findDialog;
import static org.fest.swing.finder.WindowFinder.findFrame;
import static org.fest.swing.timing.Pause.pause;
import static org.fest.swing.timing.Timeout.timeout;
import static org.fest.util.Strings.isNullOrEmpty;
import static org.fest.util.Strings.quote;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public final class GuiTests {
  public static final Timeout THIRTY_SEC_TIMEOUT = timeout(30, SECONDS);
  public static final Timeout SHORT_TIMEOUT = timeout(2, MINUTES);
  public static final Timeout LONG_TIMEOUT = timeout(5, MINUTES);

  public static final String GUI_TESTS_RUNNING_IN_SUITE_PROPERTY = "gui.tests.running.in.suite";

  /**
   * Environment variable pointing to the JDK to be used for tests
   */
  public static final String JDK_HOME_FOR_TESTS = "JDK_HOME_FOR_TESTS";

  private static final EventQueue SYSTEM_EVENT_QUEUE = Toolkit.getDefaultToolkit().getSystemEventQueue();

  private static final File TMP_PROJECT_ROOT = createTempProjectCreationDir();

  // Called by MethodInvoker via reflection
  @SuppressWarnings("unused")
  public static void failIfIdeHasFatalErrors() {
    final MessagePool messagePool = MessagePool.getInstance();
    List<AbstractMessage> fatalErrors = messagePool.getFatalErrors(true, true);
    int fatalErrorCount = fatalErrors.size();
    for (int i = 0; i < fatalErrorCount; i++) {
      System.err.println("** Fatal Error " + (i + 1) + " of " + fatalErrorCount);
      AbstractMessage error = fatalErrors.get(i);
      System.err.println("* Message: ");
      System.err.println(error.getMessage());

      String additionalInfo = error.getAdditionalInfo();
      if (isNotEmpty(additionalInfo)) {
        System.err.println("* Additional Info: ");
        System.err.println(additionalInfo);
      }

      String throwableText = error.getThrowableText();
      if (isNotEmpty(throwableText)) {
        System.err.println("* Throwable: ");
        System.err.println(throwableText);
      }
      System.err.println();
    }
    if (fatalErrorCount > 0) {
      throw new AssertionError(fatalErrorCount + " fatal errors found. Stopping test execution.");
    }
  }

  // Called by MethodInvoker via reflection
  @SuppressWarnings("unused")
  public static boolean doesIdeHaveFatalErrors() {
    final MessagePool messagePool = MessagePool.getInstance();
    List<AbstractMessage> fatalErrors = messagePool.getFatalErrors(true, true);
    return !fatalErrors.isEmpty();
  }

  // Called by IdeTestApplication via reflection.
  @SuppressWarnings("unused")
  public static void setUpDefaultGeneralSettings() {
    //setGuiTestingMode(true);

    GeneralSettings.getInstance().setShowTipsOnStartup(false);
    setUpDefaultProjectCreationLocationPath();

    setUpSdks();
  }

  public static String getSystemJdk() {
    String jdkHome = getSystemPropertyOrEnvironmentVariable(JDK_HOME_FOR_TESTS);
    if (isNullOrEmpty(jdkHome) || !checkForJdk(jdkHome)) {
      fail("Please specify the path to a valid JDK using system property " + JDK_HOME_FOR_TESTS);
    }
    return jdkHome;
  }

  public static void setUpSdks() {

    String jdkHome = getSystemPropertyOrEnvironmentVariable(JDK_HOME_FOR_TESTS);
    if (isNullOrEmpty(jdkHome) || !checkForJdk(jdkHome)) {
      fail("Please specify the path to a valid JDK using system property " + JDK_HOME_FOR_TESTS);
    }
    final File jdkPath = new File(jdkHome);

    //execute(new GuiTask() {
    //  @Override
    //  protected void executeInEDT() throws Throwable {
    //    File currentJdkPath = IdeSdks.getJdkPath();
    //    if (!filesEqual(androidSdkPath, currentAndroidSdkPath) || !filesEqual(jdkPath, currentJdkPath)) {
    //      ApplicationManager.getApplication().runWriteAction(new Runnable() {
    //        @Override
    //        public void run() {
    //          System.out.println(String.format("Setting Android SDK: '%1$s'", androidSdkPath.getPath()));
    //          IdeSdks.setAndroidSdkPath(androidSdkPath, null);
    //
    //          System.out.println(String.format("Setting JDK: '%1$s'", jdkPath.getPath()));
    //          IdeSdks.setJdkPath(jdkPath);
    //
    //          System.out.println();
    //        }
    //      });
    //    }
    //  }
    //});

  }

  @Nullable
  public static File getGradleHomePath() {
    return getFilePathProperty("supported.gradle.home.path", "the path of a local Gradle 2.2.1 distribution", true);
  }

  @Nullable
  public static File getUnsupportedGradleHome() {
    return getGradleHomeFromSystemProperty("unsupported.gradle.home.path", "2.1");
  }

  @Nullable
  public static File getGradleHomeFromSystemProperty(@NotNull String propertyName, @NotNull String gradleVersion) {
    String description = "the path of a Gradle " + gradleVersion + " distribution";
    return getFilePathProperty(propertyName, description, true);
  }


  @Nullable
  public static File getFilePathProperty(@NotNull String propertyName,
                                         @NotNull String description,
                                         boolean isDirectory) {
    String pathValue = System.getProperty(propertyName);
    if (!isNullOrEmpty(pathValue)) {
      File path = new File(pathValue);
      if (isDirectory && path.isDirectory() || !isDirectory && path.isFile()) {
        return path;
      }
    }
    System.out.println("Please specify " + description + ", using system property " + quote(propertyName));
    return null;
  }

  public static void setUpDefaultProjectCreationLocationPath() {
    RecentProjectsManager.getInstance().setLastProjectCreationLocation(getProjectCreationDirPath().getPath());
  }

  // Called by IdeTestApplication via reflection.
  @SuppressWarnings("UnusedDeclaration")
  public static void waitForIdeToStart() {
    GuiActionRunner.executeInEDT(false);
    Robot robot = null;
    try {
      robot = BasicRobot.robotWithCurrentAwtHierarchy();
      final MyProjectManagerListener listener = new MyProjectManagerListener();

      //[ACCEPT IntelliJ IDEA Privacy Policy Agreement]
      acceptAgreement(robot);
      //[Complete Installation]

      findFrame(new GenericTypeMatcher<Frame>(Frame.class) {
        @Override
        protected boolean isMatching(@NotNull Frame frame) {
          if (frame instanceof IdeFrame) {
            if (frame instanceof IdeFrameImpl) {
              listener.myActive = true;
              ProjectManager.getInstance().addProjectManagerListener(listener);
            }
            return true;
          }
          return false;
        }
      }).withTimeout(LONG_TIMEOUT.duration()).using(robot);

      // We know the IDE event queue was pushed in front of the AWT queue. Some JDKs will leave a dummy event in the AWT queue, which
      // we attempt to clear here. All other events, including those posted by the Robot, will go through the IDE event queue.
      try {
        if (SYSTEM_EVENT_QUEUE.peekEvent() != null) {
          SYSTEM_EVENT_QUEUE.getNextEvent();
        }
      }
      catch (InterruptedException ex) {
        // Ignored.
      }

      if (listener.myActive) {
        pause(new Condition("Project to be opened") {
          @Override
          public boolean test() {
            boolean notified = listener.myNotified;
            if (notified) {
              ProgressManager progressManager = ProgressManager.getInstance();
              boolean isIdle = !progressManager.hasModalProgressIndicator() &&
                               !progressManager.hasProgressIndicator() &&
                               !progressManager.hasUnsafeProgressIndicator();
              if (isIdle) {
                ProjectManager.getInstance().removeProjectManagerListener(listener);
              }
              return isIdle;
            }
            return false;
          }
        }, LONG_TIMEOUT);
      }
    }
    finally {
      GuiActionRunner.executeInEDT(true);
      if (robot != null) {
        robot.cleanUpWithoutDisposingWindows();
      }
    }
  }

  private static void acceptAgreement(Robot robot) {
    final String dialogName = ApplicationNamesInfo.getInstance().getFullProductName() + " Privacy Policy Agreement";
    try {
      final DialogFixture
        privacyDialogFixture = findDialog(new GenericTypeMatcher<JDialog>(JDialog.class) {
        @Override
        protected boolean isMatching(@NotNull JDialog dialog) {
          return dialogName.equals(dialog.getTitle()) && dialog.isShowing();
        }
      }).withTimeout(LONG_TIMEOUT.duration()).using(robot);
      String buttonText = "Accept";
      privacyDialogFixture.button(new GenericTypeMatcher<JButton>(JButton.class) {
        @Override
        protected boolean isMatching(@Nonnull JButton button) {
          return button.getText().equals(buttonText);
        }
      }).click();
    }
    catch (WaitTimedOutError we) {
      System.out.println("Timed out waiting for \"" + dialogName + "\" JDialog. Continue...");
    }
  }

  private static void completeInstallation(Robot robot) {
    final String dialogName = ApplicationBundle.message("title.complete.installation");
    try {
      final DialogFixture
        completeInstallationDialog = findDialog(dialogName)
        .withTimeout(THIRTY_SEC_TIMEOUT.duration()).using(robot);
      completeInstallationDialog.button("OK").click();
    }
    catch (WaitTimedOutError we) {
      System.out.println("Timed out waiting for \"" + dialogName + "\" JDialog. Continue...");
    }
  }

  private static void evaluateIdea(Robot robot) {
    final String dialogName = ApplicationNamesInfo.getInstance().getFullProductName() + " License Activation";
    try {
      final DialogFixture
        completeInstallationDialog = findDialog(dialogName)
        .withTimeout(THIRTY_SEC_TIMEOUT.duration()).using(robot);
      completeInstallationDialog.button("Evaluate for free for 30 days").click();
    }
    catch (WaitTimedOutError we) {
      System.out.println("Timed out waiting for \"" + dialogName + "\" JDialog. Continue...");
    }
  }

  private static void acceptLicenseAgreement(Robot robot) {
    final String dialogName = "License Agreement for" + ApplicationInfoImpl.getShadowInstance().getFullApplicationName();
    try {
      final DialogFixture
        completeInstallationDialog = findDialog(dialogName)
        .withTimeout(THIRTY_SEC_TIMEOUT.duration()).using(robot);

      completeInstallationDialog.button("Evaluate for free for 30 days").click();
    }
    catch (WaitTimedOutError we) {
      System.out.println("Timed out waiting for \"" + dialogName + "\" JDialog. Continue...");
    }
  }

  private static void customizeIdea(Robot robot) {
    final String dialogName = "Customize " + ApplicationNamesInfo.getInstance().getFullProductName();
    try {
      final DialogFixture
        completeInstallationDialog = findDialog(dialogName)
        .withTimeout(THIRTY_SEC_TIMEOUT.duration()).using(robot);

      completeInstallationDialog.button("Skip All and Set Defaults").click();
    }
    catch (WaitTimedOutError we) {
      System.out.println("Timed out waiting for \"" + dialogName + "\" JDialog. Continue...");
    }
  }

  @NotNull
  public static File getProjectCreationDirPath() {
    return TMP_PROJECT_ROOT;
  }

  @NotNull
  public static File createTempProjectCreationDir() {
    try {
      // The temporary location might contain symlinks, such as /var@ -> /private/var on MacOS.
      // EditorFixture seems to require a canonical path when opening the file.
      return createTempDir().getCanonicalFile();
    }
    catch (IOException ex) {
      // For now, keep the original behavior and point inside the source tree.
      ex.printStackTrace();
      return new File(getTestProjectsRootDirPath(), "newProjects");
    }
  }

  @NotNull
  public static File getTestProjectsRootDirPath() {

    String testDataPath = PathManagerEx.getTestDataPath(PathManagerEx.TestDataLookupStrategy.ULTIMATE);
    assertNotNull(testDataPath);
    assertThat(testDataPath).isNotEmpty();
    testDataPath = toCanonicalPath(toSystemDependentName(testDataPath));

    return new File(testDataPath, "guiTests");
  }


  private GuiTests() {
  }

  public static void deleteFile(@Nullable final VirtualFile file) {
    // File deletion must happen on UI thread under write lock
    if (file != null) {
      execute(new GuiTask() {
        @Override
        protected void executeInEDT() throws Throwable {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              try {
                file.delete(this);
              }
              catch (IOException e) {
                // ignored
              }
            }
          });
        }
      });
    }
  }

  /**
   * Waits until an IDE popup is shown (and returns it
   */
  public static JBList waitForPopup(@NotNull Robot robot) {
    return waitUntilFound(robot, null, new GenericTypeMatcher<JBList>(JBList.class) {
      @Override
      protected boolean isMatching(@NotNull JBList list) {
        ListModel model = list.getModel();
        return model instanceof ListPopupModel;
      }
    });
  }

  /**
   * Clicks an IntelliJ/Studio popup menu item with the given label prefix
   *
   * @param labelPrefix the target menu item label prefix
   * @param component   a component in the same window that the popup menu is associated with
   * @param robot       the robot to drive it with
   */
  public static void clickPopupMenuItem(@NotNull String labelPrefix, @NotNull Component component, @NotNull Robot robot) {
    clickPopupMenuItemMatching(new PrefixMatcher(labelPrefix), component, robot);
  }

  public static void clickPopupMenuItemMatching(@NotNull Matcher<String> labelMatcher, @NotNull Component component, @NotNull Robot robot) {
    // IntelliJ doesn't seem to use a normal JPopupMenu, so this won't work:
    //    JPopupMenu menu = myRobot.findActivePopupMenu();
    // Instead, it uses a JList (technically a JBList), which is placed somewhere
    // under the root pane.

    Container root = getRootContainer(component);

    // First find the JBList which holds the popup. There could be other JBLists in the hierarchy,
    // so limit it to one that is actually used as a popup, as identified by its model being a ListPopupModel:
    assertNotNull(root);
    JBList list = robot.finder().find(root, new GenericTypeMatcher<JBList>(JBList.class) {
      @Override
      protected boolean isMatching(@NotNull JBList list) {
        ListModel model = list.getModel();
        return model instanceof ListPopupModel;
      }
    });

    // We can't use the normal JListFixture method to click by label since the ListModel items are
    // ActionItems whose toString does not reflect the text, so search through the model items instead:
    ListPopupModel model = (ListPopupModel)list.getModel();
    List<String> items = Lists.newArrayList();
    for (int i = 0; i < model.getSize(); i++) {
      Object elementAt = model.getElementAt(i);
      if (elementAt instanceof PopupFactoryImpl.ActionItem) {
        PopupFactoryImpl.ActionItem item = (PopupFactoryImpl.ActionItem)elementAt;
        String s = item.getText();
        if (labelMatcher.matches(s)) {
          new JListFixture(robot, list).clickItem(i);
          return;
        }
        items.add(s);
      }
      else { // For example package private class IntentionActionWithTextCaching used in quickfix popups
        String s = elementAt.toString();
        if (labelMatcher.matches(s)) {
          new JListFixture(robot, list).clickItem(i);
          return;
        }
        items.add(s);
      }
    }

    if (items.isEmpty()) {
      fail("Could not find any menu items in popup");
    }
    fail("Did not find menu item '" + labelMatcher + "' among " + on(", ").join(items));
  }

  /**
   * Returns the root container containing the given component
   */
  @Nullable
  public static Container getRootContainer(@NotNull final Component component) {
    return execute(new GuiQuery<Container>() {
      @Override
      @Nullable
      protected Container executeInEDT() throws Throwable {
        return (Container)SwingUtilities.getRoot(component);
      }
    });
  }

  public static void findAndClickOkButton(@NotNull ContainerFixture<? extends Container> container) {
    findAndClickButton(container, "OK");
  }

  public static void findAndClickCancelButton(@NotNull ContainerFixture<? extends Container> container) {
    findAndClickButton(container, "Cancel");
  }

  public static void findAndClickButton(@NotNull ContainerFixture<? extends Container> container, @NotNull final String text) {
    Robot robot = container.robot();
    JButton button = findButton(container, text, robot);
    robot.click(button);
  }

  public static void findAndClickButtonWhenEnabled(@NotNull ContainerFixture<? extends Container> container, @NotNull final String text) {
    Robot robot = container.robot();
    final JButton button = findButton(container, text, robot);
    pause(new Condition("Wait for button " + text + " to be enabled.") {
      @Override
      public boolean test() {
        return button.isEnabled() && button.isVisible() && button.isShowing();
      }
    }, SHORT_TIMEOUT);
    robot.click(button);
  }

  public static void invokeMenuPathOnRobotIdle(IdeFrameFixture projectFrame, String... path) {
    projectFrame.robot().waitForIdle();
    projectFrame.invokeMenuPath(path);
  }

  /**
   * Opens the file with basename {@code fileBasename}
   */
  public static void openFile(IdeFrameFixture projectFrame, String fileBasename) {
    invokeMenuPathOnRobotIdle(projectFrame, "Navigate", "File...");
    projectFrame.robot().waitForIdle();
    typeText("multifunction-jni.c", projectFrame.robot(), 30);
    projectFrame.robot().pressAndReleaseKey(KeyEvent.VK_ENTER);
  }

  /**
   * Navigates to line number {@code lineNum} of the currently active editor window.
   */
  public static void navigateToLine(IdeFrameFixture projectFrame, int lineNum) {
    invokeMenuPathOnRobotIdle(projectFrame, "Navigate", "Line...");
    projectFrame.robot().enterText(Integer.toString(lineNum));
    projectFrame.robot().waitForIdle();
    projectFrame.robot().pressAndReleaseKey(KeyEvent.VK_ENTER);
  }

  private static void typeText(String text, Robot robot, long delayAfterEachCharacterMillis) {
    robot.waitForIdle();
    for (int i = 0; i < text.length(); ++i) {
      robot.type(text.charAt(i));
      Pause.pause(delayAfterEachCharacterMillis, TimeUnit.MILLISECONDS);
    }
  }

  @NotNull
  public static JButton findButton(@NotNull ContainerFixture<? extends Container> container, @NotNull final String text, Robot robot) {
    return robot.finder().find(container.target(), new GenericTypeMatcher<JButton>(JButton.class) {
      @Override
      protected boolean isMatching(@NotNull JButton button) {
        String buttonText = button.getText();
        if (buttonText != null) {
          return buttonText.trim().equals(text) && button.isShowing();
        }
        return false;
      }
    });
  }

  /** Returns a full path to the GUI data directory in the user's AOSP source tree, if known, or null */
  //@Nullable
  //public static File getTestDataDir() {
  //  File aosp = getAospSourceDir();
  //  return aosp != null ? new File(aosp, RELATIVE_DATA_PATH) : null;
  //}


  /**
   * Waits for a first component which passes the given matcher to become visible
   */
  @NotNull
  public static <T extends Component> T waitUntilFound(@NotNull final Robot robot, @NotNull final GenericTypeMatcher<T> matcher) {
    return waitUntilFound(robot, null, matcher);
  }

  public static void skip(@NotNull String testName) {
    System.out.println("Skipping test '" + testName + "'");
  }

  /**
   * Waits for a first component which passes the given matcher under the given root to become visible.
   */
  @NotNull
  public static <T extends Component> T waitUntilFound(@NotNull final Robot robot,
                                                       @Nullable final Container root,
                                                       @NotNull final GenericTypeMatcher<T> matcher) {
    final AtomicReference<T> reference = new AtomicReference<T>();
    pause(new Condition("Find component using " + matcher.toString()) {
      @Override
      public boolean test() {
        ComponentFinder finder = robot.finder();
        Collection<T> allFound = root != null ? finder.findAll(root, matcher) : finder.findAll(matcher);
        boolean found = allFound.size() == 1;
        if (found) {
          reference.set(getFirstItem(allFound));
        }
        else if (allFound.size() > 1) {
          // Only allow a single component to be found, otherwise you can get some really confusing
          // test failures; the matcher should pick a specific enough instance
          fail("Found more than one " + matcher.supportedType().getSimpleName() + " which matches the criteria: " + allFound);
        }
        return found;
      }
    }, SHORT_TIMEOUT);

    return reference.get();
  }

  /**
   * Waits until no components match the given criteria under the given root
   */
  public static <T extends Component> void waitUntilGone(@NotNull final Robot robot,
                                                         @NotNull final Container root,
                                                         @NotNull final GenericTypeMatcher<T> matcher) {
    pause(new Condition("Find component using " + matcher.toString()) {
      @Override
      public boolean test() {
        Collection<T> allFound = robot.finder().findAll(root, matcher);
        return allFound.isEmpty();
      }
    }, SHORT_TIMEOUT);
  }

  @Nullable
  public static String getSystemPropertyOrEnvironmentVariable(@NotNull String name) {
    String s = System.getProperty(name);
    return s == null ? System.getenv(name) : s;
  }

  private static class MyProjectManagerListener extends ProjectManagerAdapter {
    boolean myActive;
    boolean myNotified;

    @Override
    public void projectOpened(Project project) {
      myNotified = true;
    }
  }

  private static class PrefixMatcher extends BaseMatcher<String> {

    private final String prefix;

    public PrefixMatcher(String prefix) {
      this.prefix = prefix;
    }

    @Override
    public boolean matches(Object item) {
      return item instanceof String && ((String)item).startsWith(prefix);
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("with prefix '" + prefix + "'");
    }
  }

  public static String adduction(String s) {
    char ESCAPE_SYMBOL = '\u001B';
    String ESCAPE_SYMBOL_STRING = "" + ESCAPE_SYMBOL;
    if (s.contains(ESCAPE_SYMBOL_STRING)) {
      return StringUtil.replace(s, ESCAPE_SYMBOL_STRING, "");
    }
    else {
      return s;
    }
  }
}
