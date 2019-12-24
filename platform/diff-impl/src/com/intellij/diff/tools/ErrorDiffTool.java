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
package com.intellij.diff.tools;

import com.intellij.diff.DiffContext;
import com.intellij.diff.DiffContextEx;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.FileContent;
import com.intellij.diff.requests.*;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.vcs.changes.issueLinks.LinkMouseListenerBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static com.intellij.util.ObjectUtils.chooseNotNull;

public class ErrorDiffTool implements FrameDiffTool {
  public static final ErrorDiffTool INSTANCE = new ErrorDiffTool();

  private static final Logger LOG = Logger.getInstance(ErrorDiffTool.class);

  @NotNull
  @Override
  public DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return new MyViewer(context, request);
  }

  @Override
  public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return true;
  }

  @NotNull
  @Override
  public String getName() {
    return "Error viewer";
  }

  private static class MyViewer implements DiffViewer {
    @NotNull private final DiffContext myContext;
    @NotNull private final DiffRequest myRequest;

    @NotNull private final JPanel myPanel;

    MyViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
      myContext = context;
      myRequest = request;

      myPanel = JBUI.Panels.simplePanel(createComponent(request));
    }

    @NotNull
    private JComponent createComponent(@NotNull DiffRequest request) {
      if (request instanceof ErrorDiffRequest) {
        // TODO: explain some of ErrorDiffRequest exceptions ?
        String message = ((ErrorDiffRequest)request).getMessage();
        return createReloadMessagePanel(myContext, message, "Reload", null);
      }
      if (request instanceof MessageDiffRequest) {
        String message = ((MessageDiffRequest)request).getMessage();
        return DiffUtil.createMessagePanel(message);
      }
      if (request instanceof ComponentDiffRequest) {
        return ((ComponentDiffRequest)request).getComponent(myContext);
      }
      if (request instanceof ContentDiffRequest) {
        List<DiffContent> contents = ((ContentDiffRequest)request).getContents();
        for (final DiffContent content : contents) {
          if (content instanceof FileContent && FileTypes.UNKNOWN.equals(content.getContentType())) {
            final VirtualFile file = ((FileContent)content).getFile();
            return UnknownFileTypeDiffRequest.createComponent(file.getName(), myContext);
          }
        }
      }

      LOG.info("Can't show diff for " + request.getClass().getName());
      if (request instanceof ContentDiffRequest) {
        for (DiffContent content : ((ContentDiffRequest)request).getContents()) {
          String type = content.getContentType() != null ? content.getContentType().getName() : "null";
          LOG.info(String.format("      %s, content type: %s", content.getClass().getName(), type));
        }
      }

      return DiffUtil.createMessagePanel("Can't show diff");
    }

    @NotNull
    @Override
    public JComponent getComponent() {
      return myPanel;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return null;
    }

    @NotNull
    @Override
    public ToolbarComponents init() {
      if (myRequest instanceof UnknownFileTypeDiffRequest) {
        String fileName = ((UnknownFileTypeDiffRequest)myRequest).getFileName();
        if (fileName != null && FileTypeManager.getInstance().getFileTypeByFileName(fileName) != UnknownFileType.INSTANCE) {
          // FileType was assigned elsewhere (ex: by other UnknownFileTypeDiffRequest). We should reload request.
          if (myContext instanceof DiffContextEx) {
            ApplicationManager.getApplication().invokeLater(() -> ((DiffContextEx)myContext).reloadDiffRequest(), ModalityState.current());
          }
        }
      }

      ToolbarComponents components = new ToolbarComponents();
      components.needTopToolbarBorder = true;
      return components;
    }

    @Override
    public void dispose() {
    }
  }

  @NotNull
  public static JComponent createReloadMessagePanel(@Nullable DiffContext context, @NotNull String message,
                                                    @NotNull String reloadMessage, @Nullable Runnable beforeReload) {
    if (context instanceof DiffContextEx) {
      EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
      Color linkColor = chooseNotNull(scheme.getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR).getForegroundColor(),
                                      JBUI.CurrentTheme.Link.linkColor());

      SimpleColoredComponent textLabel = new SimpleColoredComponent();
      textLabel.setTextAlign(SwingConstants.CENTER);
      textLabel.setOpaque(false);
      textLabel.append(message);

      SimpleColoredComponent reloadLabel = new SimpleColoredComponent();
      reloadLabel.setTextAlign(SwingConstants.CENTER);
      reloadLabel.setOpaque(false);
      reloadLabel.append(reloadMessage, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, linkColor), (Runnable)() -> {
        if (beforeReload != null) beforeReload.run();
        ((DiffContextEx)context).reloadDiffRequest();
      });
      LinkMouseListenerBase.installSingleTagOn(reloadLabel);
      return DiffUtil.createMessagePanel(JBUI.Panels.simplePanel(textLabel).addToBottom(reloadLabel).andTransparent());
    }
    return DiffUtil.createMessagePanel(message);
  }
}
