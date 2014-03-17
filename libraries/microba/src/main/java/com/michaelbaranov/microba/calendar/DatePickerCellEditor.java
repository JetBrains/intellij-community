package com.michaelbaranov.microba.calendar;

import java.beans.PropertyVetoException;
import java.util.Date;

import javax.swing.DefaultCellEditor;
import javax.swing.JCheckBox;
import javax.swing.table.TableCellEditor;
import javax.swing.tree.TreeCellEditor;

import com.michaelbaranov.microba.calendar.ui.DatePickerUI;

/**
 * This class in a concrete implementation of {@link TableCellEditor} and
 * {@link TreeCellEditor} interfaces. Uses {@link DatePicker} control as en
 * editor. Subclass to extend functionality. *
 * <p>
 * Note: you probably will want to set the property of the {@link DatePicker}
 * {@value DatePicker#PROPERTY_NAME_DROPDOWN_FOCUSABLE} to <code>false</code>
 * before using it to construct {@link DatePickerCellEditor}.
 * 
 * @see DefaultCellEditor
 * 
 * @author Michael Baranov
 * 
 */
public class DatePickerCellEditor extends DefaultCellEditor {

	/**
	 * Constructor.
	 * <p>
	 * Note: you probably will want to set the property of the
	 * {@link DatePicker} {@value DatePicker#PROPERTY_NAME_DROPDOWN_FOCUSABLE}
	 * to <code>false</code> before using it to construct
	 * {@link DatePickerCellEditor}.
	 * 
	 * @param datePicker
	 *            the editor component
	 */
	public DatePickerCellEditor(final DatePicker datePicker) {
		// trick: supply a dummy JCheckBox
		super(new JCheckBox());
		// get back the dummy JCheckBox
		JCheckBox checkBox = (JCheckBox) this.editorComponent;
		// remove listeners installed by super()
		checkBox.removeActionListener(this.delegate);
		// replace editor component with own
		this.editorComponent = datePicker;

		// set simple look
		((DatePickerUI) datePicker.getUI()).setSimpeLook(true);

		// replace delegate with own
		this.delegate = new EditorDelegate() {
			public void setValue(Object value) {
				try {
					((DatePicker) editorComponent).setDate((Date) value);
				} catch (PropertyVetoException e) {
				}
			}

			public Object getCellEditorValue() {
				return ((DatePicker) editorComponent).getDate();
			}

			public void cancelCellEditing() {
				((DatePicker) editorComponent).commitOrRevert();
				super.cancelCellEditing();
			}

			public boolean stopCellEditing() {
				((DatePicker) editorComponent).commitOrRevert();
				return super.stopCellEditing();
			}

		};
		// install listeners
		datePicker.addActionListener(delegate);
		// do not set it to 1
		setClickCountToStart(2);
	}

}