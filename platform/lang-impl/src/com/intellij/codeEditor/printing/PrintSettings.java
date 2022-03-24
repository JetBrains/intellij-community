// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeEditor.printing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@State(name = "PrintSettings", storages = @Storage("print.xml"), category = SettingsCategory.UI)
public class PrintSettings implements PersistentStateComponent<PrintSettings> {
  public enum Placement {
    Header() {
      @Override public String toString() {
        return EditorBundle.message("print.header.placement.header");
      }
    },
    Footer() {
      @Override public String toString() {
        return EditorBundle.message("print.header.placement.footer");
      }
    }
  }

  public enum Alignment {
    Left() {
      @Override public String toString() {
        return EditorBundle.message("print.header.alignment.left");
      }
    },
    Center() {
      @Override public String toString() {
        return EditorBundle.message("print.header.alignment.center");
      }
    },
    Right() {
      @Override public String toString() {
        return EditorBundle.message("print.header.alignment.right");
      }
    }
  }

  public String PAPER_SIZE = "A4";

  public boolean COLOR_PRINTING = false;
  public boolean SYNTAX_PRINTING = true;
  public boolean PRINT_AS_GRAPHICS = true;

  public boolean PORTRAIT_LAYOUT = true;

  public String FONT_NAME = EditorColorsManager.getInstance().getGlobalScheme().getEditorFontName();
  public int FONT_SIZE = EditorColorsManager.getInstance().getGlobalScheme().getEditorFontSize();

  public boolean PRINT_LINE_NUMBERS = true;

  public boolean WRAP = true;

  public float TOP_MARGIN = 0.5f;
  public float BOTTOM_MARGIN = 1.0f;
  public float LEFT_MARGIN = 1.0f;
  public float RIGHT_MARGIN = 1.0f;

  public boolean DRAW_BORDER = true;

  public boolean EVEN_NUMBER_OF_PAGES = false;

  public String FOOTER_HEADER_TEXT1 = EditorBundle.message("print.header.default.line.1");
  public Placement FOOTER_HEADER_PLACEMENT1 = Placement.Header;
  public Alignment FOOTER_HEADER_ALIGNMENT1 = Alignment.Left;
  public String FOOTER_HEADER_TEXT2 = EditorBundle.message("print.header.default.line.2");
  public Placement FOOTER_HEADER_PLACEMENT2 = Placement.Footer;
  public Alignment FOOTER_HEADER_ALIGNMENT2 = Alignment.Center;
  public int FOOTER_HEADER_FONT_SIZE = 8;
  public String FOOTER_HEADER_FONT_NAME = "Arial";

  public static final int PRINT_FILE = 1;
  public static final int PRINT_SELECTED_TEXT = 2;
  public static final int PRINT_DIRECTORY = 4;
  private int myPrintScope;
  private boolean myIncludeSubdirectories;

  public static PrintSettings getInstance() {
    return ApplicationManager.getApplication().getService(PrintSettings.class);
  }

  public int getPrintScope() {
    return myPrintScope;
  }

  public void setPrintScope(int printScope) {
    myPrintScope = printScope;
  }

  public boolean isIncludeSubdirectories() {
    return myIncludeSubdirectories;
  }

  public void setIncludeSubdirectories(boolean includeSubdirectories) {
    myIncludeSubdirectories = includeSubdirectories;
  }

  @Override
  public PrintSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull final PrintSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}