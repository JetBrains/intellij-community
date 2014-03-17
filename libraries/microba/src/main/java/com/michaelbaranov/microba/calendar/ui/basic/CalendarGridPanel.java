package com.michaelbaranov.microba.calendar.ui.basic;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

import com.michaelbaranov.microba.Microba;
import com.michaelbaranov.microba.calendar.CalendarColors;
import com.michaelbaranov.microba.calendar.CalendarPane;
import com.michaelbaranov.microba.calendar.HolidayPolicy;
import com.michaelbaranov.microba.calendar.VetoPolicy;
import com.michaelbaranov.microba.common.PolicyEvent;
import com.michaelbaranov.microba.common.PolicyListener;

class CalendarGridPanel extends JPanel implements FocusListener,
		PolicyListener, PropertyChangeListener, MouseListener, KeyListener {

	public static final String PROPERTY_NAME_DATE = "date";

	public static final String PROPERTY_NAME_BASE_DATE = "baseDate";

	public static final String PROPERTY_NAME_LOCALE = "locale";

	public static final String PROPERTY_NAME_ZONE = "zone";

	public static final String PROPERTY_NAME_VETO_POLICY = "vetoPolicy";

	private static final String PROPERTY_NAME_HOLIDAY_POLICY = "holidayPolicy";

	// Not a rela property, ad-hoc approach to notify about clicking selected
	// date lable
	public static final String PROPERTY_NAME_NOTIFY_SELECTED_DATE_CLICKED = "##same date clicked##";

	private CalendarPane peer;

	private Date date;

	private Date baseDate;

	private Date focusDate;

	private Locale locale;

	private TimeZone zone;

	private VetoPolicy vetoPolicy;

	private DateLabel labels[] = new DateLabel[42];

	private Set focusableComponents = new HashSet();

	private boolean explicitDateSetToNullFlag;

	private HolidayPolicy holidayPolicy;

	private Color focusColor;

	private Color restrictedColor;

	private Color gridBgEn;

	private Color gridBgDis;

	private Color gridFgEn;

	private Color gridFgDis;

	private Color selBgEn;

	private Color selBgDis;

	private Color wkFgEn;

	private Color wkFgDis;

	private Color holFgEn;

	private Color holFgDis;

	public CalendarGridPanel(CalendarPane peer, Date date, Locale locale,
			TimeZone zone, VetoPolicy vetoDateModel, HolidayPolicy holidayPolicy) {

		this.peer = peer;

		focusColor = Microba.getOverridenColor(
				CalendarColors.COLOR_CALENDAR_GRID_FOCUS, peer, UIManager
						.getColor("TabbedPane.focus"));
		restrictedColor = Microba.getOverridenColor(
				CalendarColors.COLOR_CALENDAR_GRID_RESTRICTED, peer, Color.RED);
		gridBgEn = Microba.getOverridenColor(
				CalendarColors.COLOR_CALENDAR_GRID_BACKGROUND_ENABLED, peer,
				UIManager.getColor("TextField.background"));

		gridBgDis = Microba.getOverridenColor(
				CalendarColors.COLOR_CALENDAR_GRID_BACKGROUND_DISABLED, peer,
				UIManager.getColor("TextField.background"));

		gridFgEn = Microba.getOverridenColor(
				CalendarColors.COLOR_CALENDAR_GRID_FOREGROUND_ENABLED, peer,
				UIManager.getColor("TextField.foreground"));

		gridFgDis = Microba.getOverridenColor(
				CalendarColors.COLOR_CALENDAR_GRID_FOREGROUND_DISABLED, peer,
				UIManager.getColor("controlText"));

		selBgEn = Microba
				.getOverridenColor(
						CalendarColors.COLOR_CALENDAR_GRID_SELECTION_BACKGROUND_ENABLED,
						peer, UIManager
								.getColor("ComboBox.selectionBackground"));
		selBgDis = Microba
				.getOverridenColor(
						CalendarColors.COLOR_CALENDAR_GRID_SELECTION_BACKGROUND_DISABLED,
						peer, UIManager
								.getColor("ComboBox.selectionBackground"));

		wkFgDis = Microba.getOverridenColor(
				CalendarColors.COLOR_CALENDAR_GRID_WEEKEND_FOREGROUND_DISABLED,
				peer, gridFgDis);
		wkFgEn = Microba.getOverridenColor(
				CalendarColors.COLOR_CALENDAR_GRID_WEEKEND_FOREGROUND_ENABLED,
				peer, Color.RED);
		holFgDis = Microba.getOverridenColor(
				CalendarColors.COLOR_CALENDAR_GRID_HOLIDAY_FOREGROUND_DISABLED,
				peer, gridFgDis);
		holFgEn = Microba.getOverridenColor(
				CalendarColors.COLOR_CALENDAR_GRID_HOLIDAY_FOREGROUND_ENABLED,
				peer, Color.RED);

		this.locale = locale;
		this.zone = zone;
		this.date = date;
		this.baseDate = date == null ? new Date() : date;
		this.explicitDateSetToNullFlag = date == null ? true : false;
		this.focusDate = getFocusDateForDate(date);
		this.vetoPolicy = vetoDateModel;
		this.holidayPolicy = holidayPolicy;
		if (this.vetoPolicy != null)
			this.vetoPolicy.addVetoPolicyListener(this);
		if (this.holidayPolicy != null)
			this.holidayPolicy.addVetoPolicyListener(this);
		this.addPropertyChangeListener(this);

		setLayout(new GridLayout(6, 7, 2, 2));

		for (int i = 0; i < 42; i++) {
			DateLabel l = new DateLabel(i);
			labels[i] = l;
			l.setText(String.valueOf(i));
			l.addMouseListener(this);
			add(l);
		}
		focusableComponents.add(this);
		addKeyListener(this);
		setFocusable(true);

		// TODO: move the following to key listeners?
		InputMap input = this.getInputMap(JComponent.WHEN_FOCUSED);
		input.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
				"##microba.commit##");
		input.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0),
				"##microba.commit##");

		this.getActionMap().put("##microba.commit##", new AbstractAction() {

			public void actionPerformed(ActionEvent e) {
				Calendar c = getCalendar(focusDate);
				if (vetoPolicy == null || !vetoPolicy.isRestricted(this, c)) {
					setDate(focusDate);
				}

			}
		});
		addFocusListener(this);
		// addMouseListener(this);
		setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
		reflectData();
	}

	public void focusGained(FocusEvent e) {
		setBorder(BorderFactory.createLineBorder(focusColor));
		reflectFocusedDate();
	}

	public void focusLost(FocusEvent e) {
		setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
		reflectFocusedDate();

	}

	private void setSelectedByIndex(int i) {
		DateLabel label = labels[i];
		if (label.isVisible()) {
			int day = Integer.parseInt(label.getText());
			Calendar c = getCalendar(baseDate);
			c.set(Calendar.DAY_OF_MONTH, day);
			setDate(c.getTime());
		}
	}

	private Calendar getCalendar(Date date) {
		Calendar c = Calendar.getInstance(zone, locale);
		c.setTime(date);
		return c;
	}

	private int getSelectedIndex() {
		if (date == null)
			return -1;
		Calendar bc = getCalendar(baseDate);
		Calendar sc = getCalendar(date);
		// selectd date visible in base month
		if (bc.get(Calendar.ERA) == sc.get(Calendar.ERA)
				&& bc.get(Calendar.YEAR) == sc.get(Calendar.YEAR)
				&& bc.get(Calendar.MONTH) == sc.get(Calendar.MONTH)) {
			bc.set(Calendar.DAY_OF_MONTH, 1);
			int skipBefore = bc.get(Calendar.DAY_OF_WEEK)
					- bc.getFirstDayOfWeek();
			if (skipBefore < 0)
				skipBefore = 7 + skipBefore;
			int selDay = sc.get(Calendar.DAY_OF_MONTH);
			return skipBefore + selDay - 1;
		} else
			return -1;
	}

	private void setFocusedByIndex(int i) {
		DateLabel label = labels[i];
		if (label.isVisible()) {
			int day = Integer.parseInt(label.getText());
			Calendar c = getCalendar(baseDate);
			c.set(Calendar.DAY_OF_MONTH, day);
			setFocusDate(c.getTime());
		}
	}

	private int getFocusedIndex() {
		Calendar bc = getCalendar(baseDate);
		Calendar fc = getCalendar(focusDate);
		bc.set(Calendar.DAY_OF_MONTH, 1);
		int skipBefore = bc.get(Calendar.DAY_OF_WEEK) - bc.getFirstDayOfWeek();
		if (skipBefore < 0)
			skipBefore = 7 + skipBefore;
		int selDay = fc.get(Calendar.DAY_OF_MONTH);
		int maxDay = bc.getActualMaximum(Calendar.DAY_OF_MONTH);
		if (selDay > maxDay)
			selDay = maxDay;
		return skipBefore + selDay - 1;
	}

	private void reflectData() {
		setBackground(isEnabled() ? gridBgEn : gridBgDis);

		reflectBaseDate();
		reflectSelectedDate();
		reflectFocusedDate();
	}

	private void reflectFocusedDate() {
		int focusedIndex = getFocusedIndex();
		DateLabel l = labels[focusedIndex];
		l.setFocused(isFocusOwner());
	}

	private void reflectSelectedDate() {
		int selIndex = getSelectedIndex();
		if (selIndex > -1) {
			DateLabel l = labels[selIndex];
			l.setSelected(true);
		}
	}

	private void reflectBaseDate() {
		Calendar calendar = getCalendar(baseDate);
		calendar.set(Calendar.DAY_OF_MONTH, 1);

		int skipBefore = calendar.get(Calendar.DAY_OF_WEEK)
				- calendar.getFirstDayOfWeek();
		if (skipBefore < 0)
			skipBefore = 7 + skipBefore;

		int activeDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);

		int day = 1;
		calendar.setTime(baseDate);
		calendar.set(Calendar.DAY_OF_MONTH, 1);

		for (int i = 0; i < 42; i++) {
			DateLabel l = labels[i];
			l.setBackground(isEnabled() ? selBgEn : selBgDis);
			l.setSelected(false);
			l.setFocused(false);
			l.setEnabled(isEnabled());

			if (i < skipBefore) {
				l.setText("");
				l.setVisible(false);
			}
			if (i >= skipBefore && i < skipBefore + activeDays) {
				l.setVisible(true);
				l.setText(String.valueOf(day));
				if (vetoPolicy != null)
					l.setBanned(vetoPolicy.isRestricted(this.peer, calendar));
				else
					l.setBanned(false);

				if (holidayPolicy != null) {
					l.setDate(calendar.getTime());
					l
							.setHolliday(holidayPolicy.isHolliday(this.peer,
									calendar));
					l.setWeekend(holidayPolicy.isWeekend(this.peer, calendar));
				} else {
					l.setHolliday(false);
					l.setWeekend(false);
				}
				day++;
				calendar.add(Calendar.DAY_OF_MONTH, 1);
			}
			if (i >= skipBefore + activeDays) {
				l.setText("");
				l.setVisible(false);
			}

		}
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		Date old = this.date;
		this.date = date;
		this.explicitDateSetToNullFlag = date == null ? true : false;
		this.focusDate = getFocusDateForDate(date);
		if (old != null || date != null)
			firePropertyChange(PROPERTY_NAME_DATE, old, date);
		reflectData();
	}

	private Date getFocusDateForDate(Date date) {
		if (date == null) {
			Calendar c = getCalendar(baseDate);
			c.set(Calendar.DAY_OF_MONTH, 1);
			return c.getTime();
		}
		return date;
	}

	public Locale getLocale() {
		return locale;
	}

	public void setLocale(Locale locale) {
		Locale old = this.locale;
		this.locale = locale;
		firePropertyChange(PROPERTY_NAME_LOCALE, old, locale);
		reflectData();
	}

	public VetoPolicy getVetoPolicy() {
		return vetoPolicy;
	}

	public void setVetoPolicy(VetoPolicy vetoModel) {
		VetoPolicy old = this.getVetoPolicy();
		this.vetoPolicy = vetoModel;
		firePropertyChange(PROPERTY_NAME_VETO_POLICY, old, vetoModel);
		reflectData();
	}

	public HolidayPolicy getHolidayPolicy() {
		return holidayPolicy;
	}

	public void setHolidayPolicy(HolidayPolicy holidayPolicy) {
		HolidayPolicy old = this.getHolidayPolicy();
		this.holidayPolicy = holidayPolicy;
		firePropertyChange(PROPERTY_NAME_HOLIDAY_POLICY, old, holidayPolicy);
		reflectData();
	}

	public TimeZone getZone() {
		return zone;
	}

	public void setZone(TimeZone zone) {
		TimeZone old = this.zone;
		this.zone = zone;
		firePropertyChange(PROPERTY_NAME_ZONE, old, zone);
		reflectData();
	}

	public Collection getFocusableComponents() {
		return focusableComponents;
	}

	class DateLabel extends JLabel {

		private Date date;

		private int id;

		private boolean focused;

		private boolean selected;

		private boolean weekend;

		private boolean banned;

		private boolean holliday;

		public DateLabel(int id) {
			super();
			this.id = id;
			setHorizontalAlignment(SwingConstants.CENTER);

			setFocused(false);
			setSelected(false);
			setWeekend(false);
			setBanned(false);
			setHolliday(false);
		}

		public void setHolliday(boolean b) {
			holliday = b;
			update();
			repaint();
		}

		public int getId() {
			return id;
		}

		public boolean isFocused() {
			return focused;
		}

		public void setFocused(boolean focused) {
			this.focused = focused;

			update();
			repaint();
		}

		public boolean isSelected() {
			return selected;
		}

		public void setSelected(boolean selected) {
			this.selected = selected;
			update();
			repaint();
		}

		private void update() {
			// background is determined by selected state
			// foreground by the rest
			updateBg();
			updateFg();
			udapteBorder();
			// Tooltip:
			updateTooltip();
		}

		private void updateTooltip() {
			if (holidayPolicy != null && holliday) {
				Calendar c = Calendar.getInstance(zone, locale);
				c.setTime(date);
				setToolTipText(holidayPolicy.getHollidayName(this, c));
			} else
				setToolTipText(null);
		}

		private void udapteBorder() {
			if (isFocused() && isEnabled()) {
				setBorder(BorderFactory
						.createLineBorder(banned ? restrictedColor : focusColor));

			} else
				setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
		}

		private void updateFg() {
			if (isHolliday()) {
				setForeground(isEnabled() ? holFgEn : holFgDis);
			} else {
				if (isWeekend()) {
					setForeground(isEnabled() ? wkFgEn : wkFgDis);
				} else {
					setForeground(isEnabled() ? gridFgEn : gridFgDis);
				}
			}

		}

		private void updateBg() {
			if (isSelected()) {
				setOpaque(true);
				setBackground(isEnabled() ? selBgEn : selBgDis);
			} else
				setOpaque(false);
		}

		public boolean isWeekend() {
			return weekend;
		}

		public void setWeekend(boolean weekend) {
			this.weekend = weekend;
		}

		public boolean isBanned() {
			return banned;
		}

		public void setBanned(boolean banned) {
			this.banned = banned;
			update();
			repaint();
		}

		public void paint(Graphics g) {

			if (isBanned()) {
				g.setColor(restrictedColor);

				// variant 1: full size cross
				g.drawLine(2, 2, getWidth() - 4, getHeight() - 4);
				g.drawLine(2, getHeight() - 4, getWidth() - 4, 2);

				// variant 2: smaller cross (left upper corner)
				// g.drawLine(1, 1, 8, 8);
				// g.drawLine(1, 8, 8, 1);

			}
			super.paint(g);
		}

		public boolean isHolliday() {
			return holliday;
		}

		public Date getDate() {
			return date;
		}

		public void setDate(Date date) {
			this.date = date;
		}

	}

	public void policyChanged(PolicyEvent event) {
		reflectData();
	}

	public void propertyChange(PropertyChangeEvent evt) {
		if (evt.getPropertyName().equals(PROPERTY_NAME_VETO_POLICY)) {
			VetoPolicy oldValue = (VetoPolicy) evt.getOldValue();
			VetoPolicy newValue = (VetoPolicy) evt.getOldValue();
			if (oldValue != null)
				oldValue.removeVetoPolicyListener(this);
			if (newValue != null)
				newValue.addVetoPolicyListener(this);
			reflectData();
		}
	}

	public Date getBaseDate() {
		return baseDate;
	}

	public void setBaseDate(Date baseDate) {
		// TODO: throw away the following 2 lines?
		// if (baseDate == null)
		// baseDate = new Date();
		Date old = this.baseDate;
		this.baseDate = baseDate;
		firePropertyChange(PROPERTY_NAME_BASE_DATE, old, baseDate);

		// update focus date
		Calendar bc = getCalendar(baseDate);
		Calendar fc = getCalendar(focusDate);
		int focDate = fc.get(Calendar.DAY_OF_MONTH);
		int maxDay = bc.getActualMaximum(Calendar.DAY_OF_MONTH);
		if (focDate > maxDay)
			focDate = maxDay;
		bc.set(Calendar.DAY_OF_MONTH, focDate);
		focusDate = bc.getTime();

		reflectData();
	}

	private Date getFocusDate() {
		return focusDate;
	}

	private void setFocusDate(Date focusDate) {
		this.focusDate = focusDate;
		explicitDateSetToNullFlag = false;
		reflectData();
	}

	public void mouseClicked(MouseEvent e) {
		if (!isEnabled())
			return;
		requestFocusInWindow();
		DateLabel l = (DateLabel) e.getSource();
		if (l.isVisible()) {
			int id = Integer.parseInt(l.getText());
			Calendar c = getCalendar(baseDate);
			c.set(Calendar.DAY_OF_MONTH, id);
			if (vetoPolicy == null || !vetoPolicy.isRestricted(this, c)) {
				boolean selected = l.isSelected();
				setDate(c.getTime());
				if (selected)
					firePropertyChange(
							PROPERTY_NAME_NOTIFY_SELECTED_DATE_CLICKED, null,
							new Integer(id));
			}
		}
	}

	public void mousePressed(MouseEvent e) {

	}

	public void mouseReleased(MouseEvent e) {
	}

	public void mouseEntered(MouseEvent e) {
	}

	public void mouseExited(MouseEvent e) {
	}

	public void keyTyped(KeyEvent e) {

	}

	public void keyPressed(KeyEvent e) {
		if (!isEnabled())
			return;
		int id = getFocusedIndex();
		int row = id / 7;
		int col = id % 7;
		if (e.getKeyCode() == KeyEvent.VK_DOWN) {
			row++;
			if (row < 6) {
				setFocusedByIndex(row * 7 + col);
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_UP) {
			row--;
			if (row >= 0) {
				setFocusedByIndex(row * 7 + col);
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_LEFT) {
			col--;
			if (col >= 0) {
				setFocusedByIndex(row * 7 + col);
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
			col++;
			if (col < 7) {
				setFocusedByIndex(row * 7 + col);
			}
		}
	}

	public void keyReleased(KeyEvent e) {
	}

	public Date getDateToCommit() {
		Calendar c = getCalendar(focusDate);
		if (explicitDateSetToNullFlag
				|| (vetoPolicy != null && vetoPolicy.isRestricted(this, c)))
			return date;
		return focusDate;
	}

	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		reflectData();
	}

}
