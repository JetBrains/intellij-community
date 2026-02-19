package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.DataGridBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.text.ParsePosition;

public class NumberFormatter extends FormatterImpl {
  private final DecimalFormat myFormat;

  public NumberFormatter(@NotNull DecimalFormat format) {
    myFormat = format;
  }

  @Override
  public String format(Object value) {
    return myFormat.format(value);
  }

  @Override
  protected @Nls @NotNull String getErrorMessage() {
    return DataGridBundle.message("invalid.number");
  }

  @Override
  public Object parse(@NotNull String value, ParsePosition position) {
    return myFormat.parseObject(value, position);
  }

  public void setParseIntegerOnly(boolean value) {
    myFormat.setParseIntegerOnly(value);
  }

  public void setParseBigDecimal(boolean value) {
    myFormat.setParseBigDecimal(value);
  }

  public void setMinimumFractionDigits(int value) {
    myFormat.setMinimumFractionDigits(value);
  }

  public void setMaximumFractionDigits(int value) {
    myFormat.setMaximumFractionDigits(value);
  }

  public void setMinimumIntegerDigits(int value) {
    myFormat.setMinimumIntegerDigits(value);
  }
}
