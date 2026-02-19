package com.michaelbaranov.microba.calendar.ui.basic;

import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;
import java.text.NumberFormat;

class NoGroupingSpinner extends JSpinner {

  public static class NoGroupingNumberEditor extends NumberEditor {

    public NoGroupingNumberEditor(JSpinner spinner, SpinnerModel model) {
      super(spinner);
      JFormattedTextField ftf = (JFormattedTextField) this
          .getComponent(0);
      NumberFormat fmt = NumberFormat.getIntegerInstance();
      fmt.setGroupingUsed(false);
      ftf.setFormatterFactory(new DefaultFormatterFactory(
          new NumberFormatter(fmt)));
      revalidate();
    }

  }

  NoGroupingSpinner(SpinnerModel spinnerModel) {
    super(spinnerModel);
  }

  @Override
  protected JComponent createEditor(SpinnerModel model) {
    if (model instanceof SpinnerNumberModel)
      return new NoGroupingNumberEditor(this, model);

    return super.createEditor(model);
  }

}
