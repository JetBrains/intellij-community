// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectConfigurationProblem;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureProblemDescription;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.ui.HTMLEditorKitBuilder;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
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
import java.util.stream.IntStream;

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
  private final Runnable myOnErrorsChanged;
  private static final @NlsSafe String myStyleText = "body {" +
                                                     "  color: #" + ColorUtil.toHex(new JBColor(Gray.x33, UIUtil.getLabelForeground())) + ";" +
                                                     "  font-family: '" + StartupUiUtil.getLabelFont().getName() + ",serif';" +
                                                     "  font-size: " + StartupUiUtil.getLabelFont().getSize() + ";" +
                                                     "}" +
                                                     "li {" +
                                                     "  margin-bottom: 5;" +
                                                     "}" +
                                                     "ol {" +
                                                     "}" +
                                                     "a {" +
                                                     " text-decoration: none;" +
                                                     "}";

  public ErrorPaneConfigurable(final Project project, StructureConfigurableContext context, Runnable onErrorsChanged) {
    super(new BorderLayout());
    myOnErrorsChanged = onErrorsChanged;
    myContent.setEditorKit(HTMLEditorKitBuilder.simple());
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
            path = StringUtil.unescapeXmlEntities(path.substring(1));
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
      ConfigurationError[] errors;
      int currentStamp;
      synchronized (myLock) {
        errors = myErrors.toArray(new ConfigurationError[0]);
        currentStamp = myComputedErrorsStamp;
      }

      final HtmlChunk[] liTags = getErrorDescriptions(errors);

      final HtmlChunk.Element ol = HtmlChunk.tag("ol")
        .children(liTags);

      final HtmlChunk.Element style = HtmlChunk.tag("style")
        .attr("type", "text/css")
        .addText(myStyleText);
      final HtmlChunk.Element headerTag = new HtmlBuilder()
        .append(style)
        .wrapWith("header");

      final HtmlChunk.Element result = new HtmlBuilder()
        .append(headerTag)
        .append(HtmlChunk.body().child(ol))
        .wrapWith(HtmlChunk.html());

      myContentUpdateQueue.queue(new ShowErrorsUpdate(currentStamp, result.toString()));
      if (myOnErrorsChanged != null) {
        myOnErrorsChanged.run();
      }
    }, 100);
  }

  @Contract(pure = true)
  private static HtmlChunk @NotNull[] getErrorDescriptions(final ConfigurationError @NotNull[] errors) {
    final int limit = Math.min(errors.length, 100);

    return StreamEx.of(errors)
      .zipWith(IntStream.range(0, limit), ConfigurationErrorWithIndex::new)
      .map(ErrorPaneConfigurable::getErrorDescriptionTag)
      .toArray(HtmlChunk[]::new);
  }

  private static final class ConfigurationErrorWithIndex {
    private final @NotNull ConfigurationError myError;
    private final int myIdx;

    private ConfigurationErrorWithIndex(@NotNull final ConfigurationError error, final int idx) {
      myError = error;
      myIdx = idx;
    }
  }

  @Contract(pure = true)
  @NotNull
  private static HtmlChunk getErrorDescriptionTag(@NotNull final ConfigurationErrorWithIndex errorIndex) {
    final int index = errorIndex.myIdx;
    final ConfigurationError error = errorIndex.myError ;

    final HtmlChunk description = getErrorDescription(index, error);

    if (!error.canBeFixed()) return description.wrapWith("li");

    final String text = "[" + JavaUiBundle.message("fix.link.text") + "]";

    return new HtmlBuilder().append(description)
      .append(HtmlChunk.nbsp())
      .append(HtmlChunk.link("http://fix/" + index, text))
      .wrapWith("li");
  }

  @Contract(pure = true)
  @NotNull
  private static HtmlChunk getErrorDescription(final int index, @NotNull final ConfigurationError error) {
    //todo[nik] pass ProjectStructureProblemDescription directly and get rid of ConfigurationError at all
    if (!(error instanceof ProjectConfigurationProblem)) return error.getDescription();

    final ProjectStructureProblemDescription problemDescription = ((ProjectConfigurationProblem)error).getProblemDescription();
    if (!problemDescription.getDescription().isEmpty()) return problemDescription.getDescription();

    if (!problemDescription.canShowPlace()) return HtmlChunk.raw(problemDescription.getMessage());

    final String message = StringUtil.decapitalize(problemDescription.getMessage());

    final ProjectStructureElement place = problemDescription.getPlace().getContainingElement();
    final HtmlChunk link = HtmlChunk.link("http://navigate/" + index, place.getPresentableName());

    return new HtmlBuilder().append(place.getTypeName())
      .append(" ")
      .append(link)
      .append(": ")
      .append(message)
      .toFragment();
  }

  @Nls
  @Override
  public String getDisplayName() {
    return JavaUiBundle.message("configurable.ErrorPaneConfigurable.display.name");
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
    private final @Nls(capitalization = Nls.Capitalization.Sentence) String myText;

    ShowErrorsUpdate(int currentStamp, @Nls(capitalization = Nls.Capitalization.Sentence) String text) {
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
