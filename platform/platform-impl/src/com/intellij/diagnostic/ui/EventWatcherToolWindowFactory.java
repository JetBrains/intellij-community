// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic.ui;

import com.intellij.diagnostic.DiagnosticBundle;
import com.intellij.diagnostic.EventWatcher;
import com.intellij.diagnostic.RunnablesListener;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.DumbAwareActionButton;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

@ApiStatus.Experimental
final class EventWatcherToolWindowFactory implements ToolWindowFactory, DumbAware {

  @Override
  public void createToolWindowContent(@NotNull Project project,
                                      @NotNull ToolWindow toolWindow) {
    TableProvidingListener listener = new TableProvidingListener();

    project.getMessageBus()
      .connect(project)
      .subscribe(listener.TOPIC, listener);

    ContentManager manager = toolWindow.getContentManager();
    ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    listener.createNamedPanels()
      .map(entry -> {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(entry.getValue(),
                  BorderLayout.CENTER);

        return contentFactory.createContent(panel,
                                            entry.getKey(),
                                            false);
      }).forEach(manager::addContent);
  }

  @Override
  public void init(@NotNull ToolWindow toolWindow) {
    toolWindow.setStripeTitle(DiagnosticBundle.message("event.watcher.tab.title"));
  }

  @Override
  public boolean isApplicable(@NotNull Project project) {
    return EventWatcher.isEnabled();
  }

  private static class TableProvidingListener implements RunnablesListener {

    private final @NotNull ListTableModel<InvocationsInfo> myInvocationsModel;
    private final @NotNull ListTableModel<InvocationDescription> myRunnablesModel;
    private final @NotNull ListTableModel<WrapperDescription> myWrappersModel;

    private final @NotNull Map<@PropertyKey(resourceBundle = DiagnosticBundle.BUNDLE) String, ? extends ListTableModel<?>> myModels;

    TableProvidingListener() {
      myInvocationsModel = createDescendingTableModel(
        FunctionBasedColumnInfo.stringBased("event.watcher.column.name.runnable.callable",
                                            InvocationsInfo::getFQN),
        new FunctionBasedColumnInfo<>("event.watcher.column.name.average.duration.ms",
                                      String.class,
                                      info -> DEFAULT_DURATION_FORMAT.format(info.getAverageDuration()),
                                      Comparator.comparingDouble(InvocationsInfo::getAverageDuration)),
        new FunctionBasedColumnInfo<>("event.watcher.column.name.count",
                                      Integer.TYPE,
                                      InvocationsInfo::getCount)
      );

      myRunnablesModel = createDescendingTableModel(
        FunctionBasedColumnInfo.stringBased("event.watcher.column.name.runnable",
                                            InvocationDescription::getProcessId),
        new FunctionBasedColumnInfo<>("event.watcher.column.name.duration.ms",
                                      Long.TYPE,
                                      InvocationDescription::getDuration),
        new FunctionBasedColumnInfo<>("event.watcher.column.name.started.at",
                                      String.class,
                                      description -> DEFAULT_DATE_FORMAT.format(description.getStartDateTime()),
                                      Comparator.comparingLong(InvocationDescription::getStartedAt))
      );

      myWrappersModel = createDescendingTableModel(
        FunctionBasedColumnInfo.stringBased("event.watcher.column.name.runnable.callable",
                                            WrapperDescription::getFQN),
        new FunctionBasedColumnInfo<>("event.watcher.column.name.usages.count",
                                      Integer.TYPE,
                                      WrapperDescription::getUsagesCount)
      );

      myModels = Map.of(
        "event.watcher.tab.title.invocations", myInvocationsModel,
        "event.watcher.tab.title.runnables", myRunnablesModel,
        "event.watcher.tab.title.wrappers", myWrappersModel
      );
    }

    @NotNull Stream<Map.Entry<@Nls String, JPanel>> createNamedPanels() {
      return myModels.entrySet()
        .stream()
        .map(entry -> Map.entry(DiagnosticBundle.message(entry.getKey()),
                                createPanel(entry.getValue())));
    }

    @Override
    public void runnablesProcessed(@NotNull Collection<InvocationDescription> invocations,
                                   @NotNull Collection<InvocationsInfo> infos,
                                   @NotNull Collection<WrapperDescription> wrappers) {
      myRunnablesModel.addRows(invocations);
      setItems(myInvocationsModel, infos);
      setItems(myWrappersModel, wrappers);
    }

    private static <Item extends Comparable<? super Item>> @NotNull ListTableModel<Item> createDescendingTableModel(FunctionBasedColumnInfo<Item, ?> @NotNull ... columns) {
      return new ListTableModel<>(columns,
                                  new ArrayList<>(),
                                  1,
                                  SortOrder.DESCENDING);
    }

    private static <Item> void setItems(@NotNull ListTableModel<? super Item> model,
                                        @NotNull Collection<? extends Item> infos) {
      model.setItems(new ArrayList<>(infos));
    }

    private static @NotNull JPanel createPanel(@NotNull ListTableModel<?> tableModel) {
      return ToolbarDecorator
        .createDecorator(new TableView<>(tableModel))
        .disableUpDownActions()
        .disableAddAction()
        .disableRemoveAction()
        .setToolbarPosition(ActionToolbarPosition.RIGHT)
        .addExtraAction(new DumbAwareActionButton(DiagnosticBundle.message("event.watcher.clear.button.title"),
                                                  AllIcons.Actions.GC) {

          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            Objects.requireNonNull(EventWatcher.getInstanceOrNull())
              .reset();
            setItems(tableModel, List.of());
          }
        }).createPanel();
    }

    private static final class FunctionBasedColumnInfo<Item extends Comparable<? super Item>, Aspect extends Comparable<? super Aspect>>
      extends ColumnInfo<Item, Aspect> {

      private final @NotNull Class<? extends Aspect> myColumnClass;
      private final @NotNull Function<? super Item, ? extends Aspect> myExtractor;
      private final @NotNull Comparator<Item> myComparator;

      private FunctionBasedColumnInfo(@NotNull @PropertyKey(resourceBundle = DiagnosticBundle.BUNDLE) String nameKey,
                                      @NotNull Class<? extends Aspect> columnClass,
                                      @NotNull Function<? super Item, ? extends Aspect> extractor,
                                      @NotNull Comparator<Item> comparator) {
        super(DiagnosticBundle.message(nameKey));
        myColumnClass = columnClass;
        myExtractor = extractor;
        myComparator = comparator;
      }

      private FunctionBasedColumnInfo(@NotNull @PropertyKey(resourceBundle = DiagnosticBundle.BUNDLE) String nameKey,
                                      @NotNull Class<? extends Aspect> columnClass,
                                      @NotNull Function<? super Item, ? extends Aspect> extractor) {
        this(nameKey, columnClass, extractor, Comparator.comparing(extractor));
      }

      @Override
      public @Nullable Aspect valueOf(@NotNull Item item) {
        return myExtractor.apply(item);
      }

      @Override
      public @NotNull Class<? extends Aspect> getColumnClass() {
        return myColumnClass;
      }

      @Override
      public @NotNull Comparator<Item> getComparator() {
        return myComparator;
      }

      private static <Item extends Comparable<? super Item>> FunctionBasedColumnInfo<Item, String> stringBased(@NotNull @PropertyKey(resourceBundle = DiagnosticBundle.BUNDLE) String nameKey,
                                                                                                               @NotNull Function<? super Item, String> extractor) {
        return new FunctionBasedColumnInfo<Item, String>(nameKey,
                                                         String.class,
                                                         extractor,
                                                         Comparator.naturalOrder());
      }
    }
  }
}
