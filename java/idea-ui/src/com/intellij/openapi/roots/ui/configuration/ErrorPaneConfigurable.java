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
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
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

/**
 * @author Konstantin Bulenkov
 */
public class ErrorPaneConfigurable extends JPanel implements Configurable, Disposable, ConfigurationErrors {
  private final Alarm myAlarm;
  private final ArrayList<ConfigurationError> myErrors = new ArrayList<ConfigurationError>();
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
                  error = myErrors.get(i);
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

  public void refresh() {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
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
        for (ConfigurationError error : myErrors) {
          i++;
          if (i > 100) break;
          html.append("<li>");
          String description = error.getDescription();
          if (description.startsWith("<html>") && description.endsWith("</html>")) {
            description = description.substring(6, description.length() - 7);
          }
          if (description.startsWith("Module '")) {
            final int start = 8;
            final int end = description.indexOf("'", 9);
            final String moduleName = description.substring(start, end);
            description = "Module <a href='http://module/" + StringUtil.escapeXml(moduleName) + "'>" + StringUtil.escapeXml(moduleName) + "</a> " + description.substring(
              end + 1);
          }
          if (error.canBeFixed()) {
            description += " <a href='http://fix/" + i + "'>[Fix]</a>";
          }
          html.append(description).append("</li>");
        }
        html.append("</ol></body></html>");
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            if (!Disposer.isDisposed(ErrorPaneConfigurable.this)) {
              myContent.setText(html.toString());
            }
          }
        });
        if (myOnErrorsChanged != null) {
          myOnErrorsChanged.run();
        }
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
    myErrors.add(error);
    refresh();
  }

  @Override
  public void removeError(@NotNull ConfigurationError error) {
    myErrors.remove(error);
    refresh();
  }

  public int getErrorsCount() {
    return myErrors.size();
  }
}
