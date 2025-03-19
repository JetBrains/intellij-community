// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ide;

import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.lang.ref.WeakReference;

/**
 * This class represents usual {@link StringSelection transferable string} with additional meta-information that describes the place
 * of the document that from there it was retrieved.
 * <p/>
 * The main idea is that we want to be able to combine adjacent text into single unit like
 * <a href="http://www.gnu.org/software/emacs/manual/html_node/emacs/Kill-Ring.html#Kill-Ring">emacs kill ring</a> does.
 * E.g. if the user invokes 'cut to the line end' subsequently we may want to paste all of them, hence, we need to be able
 * to distinguish if particular copy-paste strings are adjacent.
 * <p/>
 * Thread-safe.
 */
public class KillRingTransferable implements Transferable {

  private static final DataFlavor[] DATA_FLAVORS = {DataFlavor.stringFlavor};

  private final @NotNull String         myData;
  private final WeakReference<Document> myDocument;
  private final int                     myStartOffset;
  private final int                     myEndOffset;
  private final boolean                 myCut;
  
  private volatile boolean myReadyToCombine = true;

  /**
   * Creates new {@code KillRingTransferable} object.
   * 
   * @param data                target text to transfer
   * @param document            document that contained given text
   * @param startOffset         start offset of the given text at the given document
   * @param endOffset       end offset of the given text during current object construction
   * @param cut                 flag that identifies whether target text was cut or copied from the document
   */
  public KillRingTransferable(@NotNull String data,
                              @NotNull Document document,
                              int startOffset,
                              int endOffset,
                              boolean cut)
  {
    myData = data;
    myDocument = new WeakReference<>(document);
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myCut = cut;
  }

  @Override
  public DataFlavor[] getTransferDataFlavors() {
    return DATA_FLAVORS;
  }

  @Override
  public boolean isDataFlavorSupported(DataFlavor flavor) {
    return flavor == DataFlavor.stringFlavor;
  }

  @Override
  public @NotNull Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
    if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
    return myData;
  }

  public @Nullable Document getDocument() {
    return myDocument.get();
  }

  public int getStartOffset() {
    return myStartOffset;
  }

  /**
   * @return    offset of the target text end on the moment of the current object construction
   */
  public int getEndOffset() {
    return myEndOffset;
  }

  public boolean isCut() {
    return myCut;
  }

  /**
   * @return    {@code true} if current object can be combined with adjacent text; {@code false} otherwise.
   *            Default value is {@code true}
   */
  public boolean isReadyToCombine() {
    return myReadyToCombine;
  }

  /**
   * Allows to define if current object can be combined with adjacent text. 
   * 
   * @param readyToCombine    {@code true} if current object can be combined with adjacent text; {@code false} otherwise
   */
  public void setReadyToCombine(boolean readyToCombine) {
    myReadyToCombine = readyToCombine;
  }

  @Override
  public String toString() {
    return "data='" + myData + "', startOffset=" + myStartOffset + ", endOffset=" + myEndOffset +", cut=" + myCut;
  }
}
