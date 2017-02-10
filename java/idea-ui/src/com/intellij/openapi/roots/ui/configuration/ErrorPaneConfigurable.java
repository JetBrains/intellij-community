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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectConfigurationProblem;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureProblemDescription;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.Element;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class ErrorPaneConfigurable extends JPanel implements Configurable, Disposable, ConfigurationErrors {
  private final Alarm myAlarm;
  private final List<ConfigurationError> myErrors = new ArrayList<>();
  private int myComputedErrorsStamp;
  private int myShownErrorsStamp;
  private final Object myLock = new Object();
  private final MergingUpdateQueue myContentUpdateQueue;
  private final JTextPane myContent = new JTextPane();
  private Runnable myOnErrorsChanged;

  public ErrorPaneConfigurable(final Project project, StructureConfigurableContext context, Runnable onErrorsChanged) {
    super(new BorderLayout());
    myOnErrorsChanged = onErrorsChanged;
    myContent.setEditorKit(UIUtil.getHTMLEditorKit());
    myContent.setEditable(false);
    myContent.setBackground(UIUtil.getListBackground());
    final JScrollPane pane = ScrollPaneFactory.createScrollPane(myContent, true);
    pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    add(pane);
    myContentUpdateQueue = new MergingUpdateQueue("ErrorPaneConfigurable Content Updates", 300, false, pane, this, pane);
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    project.getMessageBus().connect(this).subscribe(ConfigurationErrors.TOPIC, this);
    myContent.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      public void hyperlinkActivated(HyperlinkEvent e) {
        final URL url = e.getURL();
        final AWTEvent awtEvent = EventQueue.getCurrentEvent();
        if (!(awtEvent instanceof MouseEvent)) {
          return;
        }
        final MouseEvent me = (MouseEvent)awtEvent;

        if (url != null) {
          ConfigurationError error = null;
          Element element = e.getSourceElement();
          while (element != null) {
            if ("li".equals(element.getName())) {
              final Element ol = element.getParentElement();
              for (int i = 0; i < ol.getElementCount(); i++) {
                if (ol.getElement(i) == element) {
                  error = getError(i, myShownErrorsStamp);
                }
              }
              break;
            }
            element = element.getParentElement();
          }
          if (error == null) return;
          final String host = url.getHost();
          String path = url.getPath();
          if (path != null && path.startsWith("/")) {
            path = StringUtil.unescapeXml(path.substring(1));
          }
          if (path != null) {
            if ("fix".equals(host)) {
              final MouseEvent mouseEvent = new MouseEvent(me.getComponent(), me.getID(), me.getWhen(), me.getModifiers(),
                                                           me.getX() - 15, me.getY() + 10, me.getClickCount(), me.isPopupTrigger());
              error.fix(myContent, new RelativePoint(mouseEvent));
            } else {
              error.navigate();
            }
          }
        }
      }
    });

    refresh();
  }

  private ConfigurationError getError(int i, int expectedStamp) {
    synchronized (myLock) {
      return expectedStamp == myComputedErrorsStamp ? myErrors.get(i) : null;
    }
  }

  public void refresh() {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(() -> {
      final String header = "<html>" +
                          "<header><style type='text/css'>" +
                          "body {" +
                          "  color: #" + ColorUtil.toHex(new JBColor(Gray.x33, UIUtil.getLabelForeground())) + ";" +
                          "  font-family: '" + UIUtil.getLabelFont().getName() + ",serif';" +
                          "  font-size: " + UIUtil.getLabelFont().getSize() + ";" +
                          "}" +
                          "li {" +
                          "  margin-bottom: 5;" +
                          "}" +
                          "ol {" +
                          "}" +
                          "a {" +
                          " text-decoration: none;" +
                          "}" +
                          "</style>" +
                          "</header>" +
                          "<body>";
      final StringBuilder html = new StringBuilder(header);
      int i = 0;
      html.append("<ol>");
      ConfigurationError[] errors;
      int currentStamp;
      synchronized (myLock) {
        errors = myErrors.toArray(new ConfigurationError[0]);
        currentStamp = myComputedErrorsStamp;
      }

      for (ConfigurationError error : errors) {
        i++;
        if (i > 100) break;
        html.append("<li>");
        String description;
        if (error instanceof ProjectConfigurationProblem) {
          //todo[nik] pass ProjectStructureProblemDescription directly and get rid of ConfigurationError at all
          ProjectStructureProblemDescription problemDescription = ((ProjectConfigurationProblem)error).getProblemDescription();
          description = problemDescription.getDescription();
          if (description == null) {
            ProjectStructureElement place = problemDescription.getPlace().getContainingElement();
            description = XmlStringUtil.convertToHtmlContent(problemDescription.getMessage(false));
            if (problemDescription.canShowPlace()) {
              description = place.getTypeName() + " <a href='http://navigate/" + i + "'>"
                            + XmlStringUtil.convertToHtmlContent(place.getPresentableName()) + "</a>: "
                            + StringUtil.decapitalize(description);
            }
          }
          else {
            description = XmlStringUtil.convertToHtmlContent(description);
          }
        }
        else {
          description = XmlStringUtil.convertToHtmlContent(error.getDescription());
        }
        if (error.canBeFixed()) {
          description += " <a href='http://fix/" + i + "'>[Fix]</a>";
        }
        html.append(description).append("</li>");
      }
      html.append("</ol></body></html>");
      myContentUpdateQueue.queue(new ShowErrorsUpdate(currentStamp, html.toString()));
      if (myOnErrorsChanged != null) {
        myOnErrorsChanged.run();
      }
    }, 100);
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Problems";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return this;
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {

  }

  @Override
  public void reset() {

  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(this);
  }

  @Override
  public void dispose() {
  }

  @Override
  public void addError(@NotNull ConfigurationError error) {
    synchronized (myLock) {
      myErrors.add(error);
      myComputedErrorsStamp++;
    }
    refresh();
  }

  @Override
  public void removeError(@NotNull ConfigurationError error) {
    synchronized (myLock) {
      myErrors.remove(error);
      myComputedErrorsStamp++;
    }
    refresh();
  }

  public int getErrorsCount() {
    synchronized (myLock) {
      return myErrors.size();
    }
  }

  private class ShowErrorsUpdate extends Update {
    private final int myCurrentStamp;
    private final String myText;

    public ShowErrorsUpdate(int currentStamp, String text) {
      super(currentStamp);
      myCurrentStamp = currentStamp;
      myText = text;
    }

    @Override
    public void run() {
      if (!Disposer.isDisposed(ErrorPaneConfigurable.this)) {
        myContent.setText(myText);
        myShownErrorsStamp = myCurrentStamp;
      }
    }

    @Override
    public boolean canEat(Update update) {
      return update instanceof ShowErrorsUpdate && myCurrentStamp > ((ShowErrorsUpdate)update).myCurrentStamp;
    }
  }
}
