package com.intellij.database.run.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.database.csv.CsvFormat;
import com.intellij.database.csv.CsvFormatResolver;
import com.intellij.database.datagrid.CsvDocumentDataHookUp;
import com.intellij.database.datagrid.CsvFormatParser;
import com.intellij.database.datagrid.CsvParserResult;
import com.intellij.database.settings.CsvSettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.intellij.database.csv.CsvFormatResolverCore.simplifyFormat;

public class ChoosePasteFormatAction extends ActionGroup implements DumbAware {
  private static final String PASTE_FORMAT_KEY = "database.data.paste.format";

  public ChoosePasteFormatAction() {
    setPopup(true);
  }

  public ChoosePasteFormatAction(@NotNull @NlsActions.ActionText String name) {
    super(name, true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(!CsvSettings.getSettings().getCsvFormats().isEmpty());
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {

    AnAction showFormatsAction = ActionManager.getInstance().getAction("Console.TableResult.Copy.Csv.Settings.ForImport");

    return ContainerUtil.skipNulls(ContainerUtil.concat(
      Arrays.asList(new PasteTypeAction(PasteType.AUTO, DataGridBundle.message("action.Console.TableResult.PasteFormat.detect")),
                    new PasteTypeAction(PasteType.SINGLE_VALUE, DataGridBundle.message("action.Console.TableResult.PasteFormat.single.value")),
                    new Separator()),
      ContainerUtil.map(CsvSettings.getSettings().getCsvFormats(), f -> new FormatAction(f)),
      Arrays.asList(new Separator(), showFormatsAction)
    )).toArray(AnAction.EMPTY_ARRAY);
  }

  private static class PasteTypeAction extends ToggleAction implements DumbAware {
    protected final PasteType myType;

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    PasteTypeAction(@NotNull PasteType type, @NlsActions.ActionText @NotNull String name) {
      super(name);
      myType = type;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return PasteType.get() == myType;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      if (state) myType.save(getFormat());
    }

    @Nullable
    CsvFormat getFormat() {
      return null;
    }
  }

  private static class FormatAction extends PasteTypeAction {
    private final CsvFormat myFormat;

    FormatAction(@NotNull CsvFormat format) {
      super(PasteType.FORMAT, format.name);
      myFormat = format;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      CsvFormat format = myType.getFormat();
      return super.isSelected(e) && StringUtil.equals(myFormat.id, format == null ? null : format.id);
    }

    @Nullable
    @Override
    CsvFormat getFormat() {
      return myFormat;
    }
  }

  public enum PasteType {
    AUTO {
      @Override
      public @NotNull String getValueToSave(@Nullable CsvFormat format) {
        return AUTO_PREFIX;
      }

      @Override
      public boolean isCurrent(@NotNull String value) {
        return value.equals(AUTO_PREFIX);
      }

      @Override
      public @NotNull Parser getParser() {
        return new FormatParser() {
          @Nullable
          @Override
          CsvFormat getFormat(@NotNull Project project, @NotNull String text) {
            return CsvFormatResolver.getFormat(project, new LightVirtualFile("dummy", text), false, null);
          }
        };
      }

      @Override
      public @Nullable CsvFormat getFormat() {
        return null;
      }
    },
    SINGLE_VALUE {
      @Override
      public @NotNull String getValueToSave(@Nullable CsvFormat format) {
        return SINGLE_PREFIX;
      }

      @Override
      public boolean isCurrent(@NotNull String value) {
        return value.equals(SINGLE_PREFIX);
      }

      @Override
      public @NotNull Parser getParser() {
        return SingleValueParser.INSTANCE;
      }

      @Override
      public @Nullable CsvFormat getFormat() {
        return null;
      }
    },
    FORMAT {
      @Override
      public @NotNull String getValueToSave(@Nullable CsvFormat format) {
        return FORMAT_PREFIX + Objects.requireNonNull(format).id;
      }

      @Override
      public boolean isCurrent(@NotNull String value) {
        return value.startsWith(FORMAT_PREFIX);
      }

      @Override
      public @NotNull Parser getParser() {
        CsvFormat format = simplifyFormat(getFormat());
        return format == null ? SingleValueParser.INSTANCE : new FormatParser() {
          @Override
          CsvFormat getFormat(@NotNull Project project, @NotNull String text) {
            return format;
          }
        };
      }

      @Override
      public @Nullable CsvFormat getFormat() {
        String value = PropertiesComponent.getInstance().getValue(PASTE_FORMAT_KEY);
        String id = value == null || value.length() <= FORMAT_PREFIX.length() ? null : value.substring(FORMAT_PREFIX.length());
        List<CsvFormat> formats = CsvSettings.getSettings().getCsvFormats();
        return id == null ? null : ContainerUtil.find(formats, f -> f.id.equals(id));
      }
    };

    private static final String FORMAT_PREFIX = "format:";
    private static final String AUTO_PREFIX = "auto";
    private static final String SINGLE_PREFIX = "single";

    public void save(@Nullable CsvFormat format) {
      PropertiesComponent.getInstance().setValue(PASTE_FORMAT_KEY, getValueToSave(format));
    }

    public abstract @NotNull Parser getParser();

    public abstract @NotNull String getValueToSave(@Nullable CsvFormat format);

    public abstract @Nullable CsvFormat getFormat();

    public abstract boolean isCurrent(@NotNull String value);

    public static @NotNull PasteType get() {
      String value = PropertiesComponent.getInstance().getValue(PASTE_FORMAT_KEY);
      if (value != null) {
        for (PasteType type : values()) {
          if (type.isCurrent(value)) return type;
        }
      }
      return AUTO;
    }
  }

  public interface Parser {
    @NotNull
    Pair<List<String[]>, CsvFormat> parse(@NotNull Project project, @NotNull String value);
  }

  private static class SingleValueParser implements Parser {
    static final SingleValueParser INSTANCE = new SingleValueParser();

    @Override
    public @NotNull Pair<List<String[]>, CsvFormat> parse(@NotNull Project project, @NotNull String value) {
      return new Pair<>(Collections.singletonList(new String[]{value}), null);
    }
  }

  private abstract static class FormatParser implements Parser {
    @Override
    public @NotNull Pair<List<String[]>, CsvFormat> parse(@NotNull Project project, @NotNull String value) {
      CsvFormat format = getFormat(project, value);
      CsvParserResult result = format == null ? null : new CsvFormatParser(format).parse(value);
      if (result == null) {
        return SingleValueParser.INSTANCE.parse(project, value);
      }
      List<String[]> values = ContainerUtil
        .map(CsvDocumentDataHookUp.rowsFrom(result.getFormat(), result.getSequence(), result.getRecords(),
                                            result.getFormat().rowNumbers),
             row -> ContainerUtil.map(row, o -> o == null ? null : o.toString()).toArray(ArrayUtilRt.EMPTY_STRING_ARRAY));
      return new Pair<>(values, format);
    }

    abstract @Nullable CsvFormat getFormat(@NotNull Project project, @NotNull String text);
  }
}