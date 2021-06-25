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
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.ToggleActionButton;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.Producer;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;

@ApiStatus.Experimental
final class EventWatcherToolWindowFactory implements ToolWindowFactory, DumbAware {

  private static final int MS_FOR_FPS_60 = 16;

  @Override
  public void createToolWindowContent(@NotNull Project project,
                                      @NotNull ToolWindow toolWindow) {
    TableProvidingListener listener = new TableProvidingListener();

    project.getMessageBus()
      .connect(project)
      .subscribe(listener.TOPIC, listener);

    listener.getContents()
      .forEach(toolWindow.getContentManager()::addContent);
  }

  @Override
  public void init(@NotNull ToolWindow toolWindow) {
    toolWindow.setStripeTitle(DiagnosticBundle.message("tab.title.event.watcher"));
  }

  @Override
  public boolean isApplicable(@NotNull Project project) {
    return EventWatcher.isEnabled();
  }

  private static class TableProvidingListener implements RunnablesListener {

    private final @NotNull ListTableModel<InvocationsInfo> myInvocationsModel;
    private final @NotNull ListTableModel<InvocationDescription> myRunnablesModel;
    private final @NotNull ListTableModel<WrapperDescription> myWrappersModel;

    private final @NotNull AtomicBoolean myInvocationsFilterEnabled = new AtomicBoolean(false);
    private final @NotNull AtomicBoolean myRunnablesFilterEnabled = new AtomicBoolean(false);

    private final @NotNull List<Content> myContents;

    TableProvidingListener() {
      myInvocationsModel = new ListTableModel<>(
        new ColumnInfo[]{
          FunctionBasedColumnInfo.stringBased(DiagnosticBundle.message("event.watcher.column.name.runnable.callable"),
                                              InvocationsInfo::getFQN),
          new FunctionBasedColumnInfo<>(DiagnosticBundle.message("event.watcher.column.name.average.duration.ms"),
                                        String.class,
                                        info -> DEFAULT_DURATION_FORMAT.format(info.getAverageDuration()),
                                        Comparator.comparingDouble(InvocationsInfo::getAverageDuration)),
          new FunctionBasedColumnInfo<>(DiagnosticBundle.message("event.watcher.column.name.count"),
                                        Integer.TYPE,
                                        InvocationsInfo::getCount)
        },
        new ArrayList<>(),
        1,
        SortOrder.DESCENDING
      );

      myRunnablesModel = new ListTableModel<>(
        FunctionBasedColumnInfo.stringBased(DiagnosticBundle.message("event.watcher.column.name.runnable"),
                                            InvocationDescription::getProcessId),
        new FunctionBasedColumnInfo<>(DiagnosticBundle.message("event.watcher.column.name.duration.ms"),
                                      Long.TYPE,
                                      InvocationDescription::getDuration),
        new FunctionBasedColumnInfo<>(DiagnosticBundle.message("event.watcher.column.name.started.at"),
                                      String.class,
                                      description -> DEFAULT_DATE_FORMAT.format(description.getStartDateTime()),
                                      Comparator.comparingLong(InvocationDescription::getStartedAt))
      );

      myWrappersModel = new ListTableModel<>(
        FunctionBasedColumnInfo.stringBased(DiagnosticBundle.message("event.watcher.column.name.runnable.callable"),
                                            WrapperDescription::getFQN),
        new FunctionBasedColumnInfo<>(DiagnosticBundle.message("event.watcher.column.name.usages.count"),
                                      Integer.TYPE,
                                      WrapperDescription::getUsagesCount)
      );

      myContents = Arrays.asList(
        // XXX Get rid of all of those lambdas
        createTableContent(DiagnosticBundle.message("event.watcher.tab.title.invocations"), myInvocationsModel,
                           o -> !myInvocationsFilterEnabled.get() || myInvocationsModel.getRowValue(o).getAverageDuration() > MS_FOR_FPS_60,
                           () -> myInvocationsFilterEnabled.set(!myInvocationsFilterEnabled.get()),
                           () -> myInvocationsFilterEnabled.get()),
        createTableContent(DiagnosticBundle.message("event.watcher.tab.title.runnables"), myRunnablesModel,
                           o -> !myRunnablesFilterEnabled.get() || myRunnablesModel.getRowValue(o).getDuration() > MS_FOR_FPS_60,
                           () -> myRunnablesFilterEnabled.set(!myRunnablesFilterEnabled.get()),
                           () -> myRunnablesFilterEnabled.get()),
        createTableContent(DiagnosticBundle.message("event.watcher.tab.title.wrappers"), myWrappersModel, null, null, null)
      );
    }

    @Override
    public void runnablesProcessed(@NotNull Collection<InvocationDescription> invocations,
                                   @NotNull Collection<InvocationsInfo> infos,
                                   @NotNull Collection<WrapperDescription> wrappers) {
      myRunnablesModel.addRows(invocations);
      myInvocationsModel.setItems(new ArrayList<>(infos));
      myWrappersModel.setItems(new ArrayList<>(wrappers));
    }

    @NotNull List<? extends Content> getContents() {
      return myContents;
    }

    private static @NotNull Content createTableContent(@NotNull @NlsContexts.TabTitle String tableName,
                                                       @NotNull ListTableModel<?> tableModel,
                                                       @Nullable Predicate<Integer> filter,
                                                       @Nullable Runnable filterSwitcher,
                                                       @Nullable Producer<Boolean> filterEnabled) {
      JPanel panel = new JPanel(new BorderLayout());
      TableView<?> tableView = new TableView<>(tableModel);
      ToolbarDecorator decorator = ToolbarDecorator.createDecorator(tableView);
      decorator.disableUpDownActions();
      decorator.disableAddAction();
      decorator.disableRemoveAction();
      decorator.setToolbarPosition(ActionToolbarPosition.RIGHT);
      decorator.addExtraAction(new AnActionButton("Clear All", AllIcons.Actions.GC) { //NON-NLS
        @Override
        public boolean isDumbAware() {
          return true;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          EventWatcher watcher = EventWatcher.getInstanceOrNull();
          if (watcher != null) {
            watcher.reset();
          }
          tableModel.setItems(new ArrayList<>());
        }
      });
      if (filter != null) {
        decorator.addExtraAction(new ToggleActionButton("Filter 16ms", AllIcons.General.Filter) { //NON-NLS

          @Override
          public boolean isDumbAware() {
            return true;
          }

          @Override
          public boolean isSelected(AnActionEvent e) {
            if (filterEnabled == null) {
              return false;
            }
            else {
              return filterEnabled.produce();
            }
          }

          @Override
          public void setSelected(AnActionEvent e, boolean state) {
            if (filterSwitcher != null) {
              filterSwitcher.run();
              panel.repaint();
            }
          }
        });

        TableRowSorter<TableModel> sorter = new TableRowSorter<>(tableModel);
        sorter.setRowFilter(new RowFilter<>() {
          @Override
          public boolean include(Entry<? extends TableModel, ? extends Integer> entry) {
            return filter.test(entry.getIdentifier());
          }
        });
        tableView.setRowSorter(sorter);
      }
      panel.add(decorator.createPanel(), BorderLayout.CENTER);

      return ContentFactory.SERVICE
        .getInstance()
        .createContent(panel, tableName, false);
    }

    private static final class FunctionBasedColumnInfo<Item extends Comparable<? super Item>, Aspect extends Comparable<? super Aspect>>
      extends ColumnInfo<Item, Aspect> {

      private final @NotNull Class<? extends Aspect> myColumnClass;
      private final @NotNull Function<? super Item, ? extends Aspect> myExtractor;
      private final @NotNull Comparator<Item> myComparator;

      private FunctionBasedColumnInfo(@NotNull @Nls String name,
                                      @NotNull Class<? extends Aspect> columnClass,
                                      @NotNull Function<? super Item, ? extends Aspect> extractor,
                                      @NotNull Comparator<Item> comparator) {
        super(name);
        myColumnClass = columnClass;
        myExtractor = extractor;
        myComparator = comparator;
      }

      private FunctionBasedColumnInfo(@NotNull @Nls String name,
                                      @NotNull Class<? extends Aspect> columnClass,
                                      @NotNull Function<? super Item, ? extends Aspect> extractor) {
        this(name, columnClass, extractor, Comparator.comparing(extractor));
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

      private static <Item extends Comparable<? super Item>> FunctionBasedColumnInfo<Item, String> stringBased(@NotNull @NlsContexts.ColumnName String name,
                                                                                                               @NotNull Function<? super Item, String> extractor) {
        return new FunctionBasedColumnInfo<Item, String>(name, String.class, extractor, Comparator.naturalOrder());
      }
    }
  }
}
