// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.injected.changesHandler;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class CommonInjectedFileChangesHandler extends BaseInjectedFileChangesHandler {
  private final List<Trinity<RangeMarker, RangeMarker, SmartPsiElementPointer>> myMarkers = ContainerUtil.newLinkedList();

  public CommonInjectedFileChangesHandler(List<PsiLanguageInjectionHost.Shred> shreds,
                                          Editor editor,
                                          Document newDocument,
                                          PsiFile injectedFile) {
    super(editor, newDocument, injectedFile);

    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(myProject);
    int curOffset = -1;
    for (PsiLanguageInjectionHost.Shred shred : shreds) {
      final RangeMarker rangeMarker = myNewDocument.createRangeMarker(
        shred.getRange().getStartOffset() + shred.getPrefix().length(),
        shred.getRange().getEndOffset() - shred.getSuffix().length());
      final TextRange rangeInsideHost = shred.getRangeInsideHost();
      PsiLanguageInjectionHost host = shred.getHost();
      RangeMarker origMarker = myOrigDocument.createRangeMarker(rangeInsideHost.shiftRight(host.getTextRange().getStartOffset()));
      SmartPsiElementPointer<PsiLanguageInjectionHost> elementPointer = smartPointerManager.createSmartPsiElementPointer(host);
      Trinity<RangeMarker, RangeMarker, SmartPsiElementPointer> markers =
        Trinity.create(origMarker, rangeMarker, elementPointer);
      myMarkers.add(markers);

      origMarker.setGreedyToRight(true);
      rangeMarker.setGreedyToRight(true);
      if (origMarker.getStartOffset() > curOffset) {
        origMarker.setGreedyToLeft(true);
        rangeMarker.setGreedyToLeft(true);
      }
      curOffset = origMarker.getEndOffset();
    }
  }

  @Override
  public boolean isValid() {
    boolean valid = myInjectedFile.isValid();
    if (valid) {
      for (Trinity<RangeMarker, RangeMarker, SmartPsiElementPointer> t : myMarkers) {
        if (!t.first.isValid() || !t.second.isValid() || t.third.getElement() == null) {
          valid = false;
          break;
        }
      }
    }
    return valid;
  }

  @Override
  public void commitToOriginal(@NotNull DocumentEvent e) {
    final String text = myNewDocument.getText();
    final Map<PsiLanguageInjectionHost, Set<Trinity<RangeMarker, RangeMarker, SmartPsiElementPointer>>> map = ContainerUtil
      .classify(myMarkers.iterator(),
                o -> {
                  final PsiElement element = o.third.getElement();
                  return (PsiLanguageInjectionHost)element;
                });
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    documentManager.commitDocument(myOrigDocument); // commit here and after each manipulator update
    int localInsideFileCursor = 0;
    for (PsiLanguageInjectionHost host : map.keySet()) {
      if (host == null) continue;
      String hostText = host.getText();
      ProperTextRange insideHost = null;
      StringBuilder sb = new StringBuilder();
      for (Trinity<RangeMarker, RangeMarker, SmartPsiElementPointer> entry : map.get(host)) {
        RangeMarker origMarker = entry.first; // check for validity?
        int hostOffset = host.getTextRange().getStartOffset();
        ProperTextRange localInsideHost =
          new ProperTextRange(origMarker.getStartOffset() - hostOffset, origMarker.getEndOffset() - hostOffset);
        RangeMarker rangeMarker = entry.second;
        ProperTextRange localInsideFile =
          new ProperTextRange(Math.max(localInsideFileCursor, rangeMarker.getStartOffset()), rangeMarker.getEndOffset());
        if (insideHost != null) {
          //append unchanged inter-markers fragment
          sb.append(hostText, insideHost.getEndOffset(), localInsideHost.getStartOffset());
        }
        sb.append(localInsideFile.getEndOffset() <= text.length() && !localInsideFile.isEmpty() ? localInsideFile.substring(text) : "");
        localInsideFileCursor = localInsideFile.getEndOffset();
        insideHost = insideHost == null ? localInsideHost : insideHost.union(localInsideHost);
      }
      assert insideHost != null;
      updateInjectionHostElement(host, insideHost, sb.toString());
      documentManager.commitDocument(myOrigDocument);
    }
  }

  protected void updateInjectionHostElement(PsiLanguageInjectionHost host, ProperTextRange insideHost, String content) {
    ElementManipulators.getManipulator(host).handleContentChange(host, insideHost, content);
  }

  @Override
  public boolean handlesRange(@NotNull TextRange range) {
    if (!myMarkers.isEmpty()) {
      TextRange hostRange = TextRange.create(myMarkers.get(0).first.getStartOffset(),
                                             myMarkers.get(myMarkers.size() - 1).first.getEndOffset());
      return range.intersects(hostRange);
    }
    return false;
  }
}
