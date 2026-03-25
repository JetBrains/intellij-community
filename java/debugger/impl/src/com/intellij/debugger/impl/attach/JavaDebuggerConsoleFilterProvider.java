// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.attach;

import com.intellij.codeInsight.hints.InlayContentListener;
import com.intellij.codeInsight.hints.presentation.DynamicDelegatePresentation;
import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.codeInsight.hints.presentation.PresentationRenderer;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.actions.JavaDebuggerActionsCollector;
import com.intellij.debugger.impl.DebuggerManagerListener;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.execution.filters.ConsoleFilterProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.impl.InlayProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JavaDebuggerConsoleFilterProvider implements ConsoleFilterProvider {
  static final Pattern PATTERN = Pattern.compile("Listening for transport (\\S+) at address: (\\S+)");

  @Override
  public Filter @NotNull [] getDefaultFilters(@NotNull Project project) {
    return new Filter[]{new JavaDebuggerAttachFilter(project)};
  }

  public static Matcher getConnectionMatcher(String line) {
    if (line.contains("Listening for transport")) {
      Matcher matcher = PATTERN.matcher(line);
      if (matcher.find()) {
        return matcher;
      }
    }
    return null;
  }

  private static class JavaDebuggerAttachFilter implements Filter {
    @NotNull Project myProject;

    private JavaDebuggerAttachFilter(@NotNull Project project) {
      this.myProject = project;
    }

    @Override
    public @Nullable Result applyFilter(@NotNull String line, int entireLength) {
      Matcher matcher = getConnectionMatcher(line);
      if (matcher == null) {
        return null;
      }
      String transport = matcher.group(1);
      String address = matcher.group(2);
      int start = entireLength - line.length();

      if (Registry.is("debugger.auto.attach.from.any.console") && !isDebuggerAttached(transport, address, myProject)) {
        ApplicationManager.getApplication().invokeLater(
          () -> JavaAttachDebuggerProvider.attach(transport, address, null, myProject),
          ModalityState.any());
      }

      // to trick the code unwrapping single results in com.intellij.execution.filters.CompositeFilter#createFinalResult
      return new Result(Arrays.asList(
        new AttachInlayResult(start + matcher.start(), start + matcher.end(), transport, address, myProject),
        new ResultItem(0, 0, null)));
    }
  }

  private static boolean isDebuggerAttached(String transport, String address, Project project) {
    return DebuggerManagerEx.getInstanceEx(project).getSessions()
      .stream()
      .map(s -> s.getDebugEnvironment().getRemoteConnection())
      .anyMatch(c -> address.equals(c.getApplicationAddress()) && "dt_shmem".equals(transport) != c.isUseSockets());
  }

  private static class AttachInlayResult extends Filter.ResultItem implements InlayProvider {
    private final String myTransport;
    private final String myAddress;
    private final @NotNull Project myProject;

    AttachInlayResult(int highlightStartOffset,
                      int highlightEndOffset,
                      String transport,
                      String address,
                      @NotNull Project project) {
      super(highlightStartOffset, highlightEndOffset, null);
      myTransport = transport;
      myAddress = address;
      myProject = project;
    }

    @Override
    public @Nullable Inlay<?> createInlay(@NotNull Editor editor, int offset) {
      AttachDebuggerInlayPresentation presentation = new AttachDebuggerInlayPresentation(editor, myTransport, myAddress, myProject);
      if (!presentation.isAttached()) {
        JavaDebuggerActionsCollector.attachFromConsoleInlayShown.log();
      }
      Inlay<?> inlay = editor.getInlayModel().addInlineElement(offset, new PresentationRenderer(presentation));
      if (inlay == null) {
        return null;
      }
      presentation.installListeners(inlay);
      return inlay;
    }
  }

  private static final class AttachDebuggerInlayPresentation extends DynamicDelegatePresentation {
    private final @NotNull InlayPresentation myAttachPresentation;
    private final @NotNull InlayPresentation myAttachedPresentation;
    private final @NotNull String myTransport;
    private final @NotNull String myAddress;
    private final @NotNull Project myProject;

    private AttachDebuggerInlayPresentation(@NotNull Editor editor,
                                            @NotNull String transport,
                                            @NotNull String address,
                                            @NotNull Project project) {
      this(createAttachPresentation(editor, transport, address, project),
           createAttachedPresentation(editor),
           transport,
           address,
           project);
    }

    private AttachDebuggerInlayPresentation(@NotNull InlayPresentation attachPresentation,
                                            @NotNull InlayPresentation attachedPresentation,
                                            @NotNull String transport,
                                            @NotNull String address,
                                            @NotNull Project project) {
      super(isDebuggerAttached(transport, address, project) ? attachedPresentation : attachPresentation);
      myAttachPresentation = attachPresentation;
      myAttachedPresentation = attachedPresentation;
      myTransport = transport;
      myAddress = address;
      myProject = project;
    }

    private boolean isAttached() {
      return getDelegate() == myAttachedPresentation;
    }

    private void installListeners(@NotNull Inlay<?> inlay) {
      addListener(new InlayContentListener(inlay));
      myProject.getMessageBus().connect(inlay).subscribe(DebuggerManagerListener.TOPIC, new DebuggerManagerListener() {
        @Override
        public void sessionCreated(DebuggerSession session) {
          updateLater(inlay);
        }

        @Override
        public void sessionRemoved(DebuggerSession session) {
          updateLater(inlay);
        }
      });
      update();
    }

    private void updateLater(@NotNull Inlay<?> inlay) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (!inlay.isValid()) {
          return;
        }
        update();
      }, ModalityState.any());
    }

    private void update() {
      InlayPresentation delegate = isDebuggerAttached(myTransport, myAddress, myProject) ? myAttachedPresentation : myAttachPresentation;
      if (getDelegate() != delegate) {
        setDelegate(delegate);
      }
    }

    private static @NotNull InlayPresentation createAttachPresentation(@NotNull Editor editor,
                                                                       @NotNull String transport,
                                                                       @NotNull String address,
                                                                       @NotNull Project project) {
      PresentationFactory factory = new PresentationFactory(editor);
      return factory.referenceOnHover(
        factory.roundWithBackground(factory.smallText(JavaDebuggerBundle.message("debugger.console.inlay.attach"))),
        (event, point) -> {
          JavaDebuggerActionsCollector.attachFromConsoleInlay.log();
          JavaAttachDebuggerProvider.attach(transport, address, null, project);
        });
    }

    private static @NotNull InlayPresentation createAttachedPresentation(@NotNull Editor editor) {
      PresentationFactory factory = new PresentationFactory(editor);
      return factory.roundWithBackground(factory.smallText(JavaDebuggerBundle.message("debugger.console.inlay.attached")));
    }
  }
}
