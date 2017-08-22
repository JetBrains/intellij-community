/*
	Thinlet GUI toolkit - www.thinlet.com
	Copyright (C) 2002 Robert Bajzat (robert.bajzat@thinlet.com)

	This library is free software; you can redistribute it and/or
	modify it under the terms of the GNU Lesser General Public
	License as published by the Free Software Foundation; either
	version 2.1 of the License, or (at your option) any later version.

	This library is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
	Lesser General Public License for more details.

	You should have received a copy of the GNU Lesser General Public
	License along with this library; if not, write to the Free Software
	Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

//package thinlet; //java
//midp package thinlet.midp;

import java.awt.*; //java
import java.awt.<error descr="Cannot resolve symbol 'datatransfer'">datatransfer</error>.*; //java
import java.awt.<error descr="Cannot resolve symbol 'image'">image</error>.*; //java
import java.awt.event.*; //java
import java.lang.reflect.*; //java
import java.io.*;
import java.net.*; //java
import java.util.*;
//midp import javax.microedition.lcdui.*;
//midp import javax.microedition.midlet.*;

/**
 *
 */
class Thinlet extends Container //java
	implements Runnable, Serializable { //java
//midp public class Thinlet extends Canvas implements CommandListener {

	//midp private static final Boolean TRUE = new Boolean(true);
	//midp private static final Boolean FALSE = new Boolean(false);

	//midp private transient Font font;
	private transient <error descr="Cannot resolve symbol 'Color'">Color</error> c_bg;
	private transient <error descr="Cannot resolve symbol 'Color'">Color</error> c_text;
	private transient <error descr="Cannot resolve symbol 'Color'">Color</error> c_textbg;
	private transient <error descr="Cannot resolve symbol 'Color'">Color</error> c_border;
	private transient <error descr="Cannot resolve symbol 'Color'">Color</error> c_disable;
	private transient <error descr="Cannot resolve symbol 'Color'">Color</error> c_hover;
	private transient <error descr="Cannot resolve symbol 'Color'">Color</error> c_press;
	private transient <error descr="Cannot resolve symbol 'Color'">Color</error> c_focus;
	private transient <error descr="Cannot resolve symbol 'Color'">Color</error> c_select;
	private transient <error descr="Cannot resolve symbol 'Color'">Color</error> c_ctrl = null; //java
	//midp private transient Color c_ctrl;
	private transient int block;
	private transient <error descr="Cannot resolve symbol 'Image'">Image</error> gradient; //java
	{
		setFont(new <error descr="Cannot resolve symbol 'Font'">Font</error>("SansSerif", <error descr="Cannot resolve symbol 'Font'">Font</error>.PLAIN, 12)); //java
		//midp setFont(Font.getDefaultFont());
		setColors(0xe6e6e6, 0x000000, 0xffffff,
			0x909090, 0xb0b0b0, 0xededed, 0xb9b9b9, 0x89899a, 0xc5c5dd); // f99237 eac16a // e68b2c ffc73c
	}

	private transient Thread timer;
	private transient long watchdelay;
	private transient long watch;
	private transient String clipboard;

	private Object content = createImpl("desktop");
	private transient Object mouseinside;
	private transient Object insidepart;
	private transient Object mousepressed;
	private transient Object pressedpart;
	private transient int referencex, referencey;
	private transient int mousex, mousey;
	private transient Object focusowner;
	private transient boolean focusinside; //midp { focusinside = true; }
	private transient Object popupowner;
	private transient Object tooltipowner;
	//private transient int pressedkey;

	//java>
	private static final int DRAG_ENTERED = <error descr="Cannot resolve symbol 'AWTEvent'">AWTEvent</error>.RESERVED_ID_MAX + 1;
	private static final int DRAG_EXITED = <error descr="Cannot resolve symbol 'AWTEvent'">AWTEvent</error>.RESERVED_ID_MAX + 2;

	private static long WHEEL_MASK = 0;
	private static int MOUSE_WHEEL = 0;
	private static <error descr="Cannot resolve symbol 'Method'">Method</error> wheelrotation = null;
	static {
		try {
			WHEEL_MASK = <error descr="Cannot resolve symbol 'AWTEvent'">AWTEvent</error>.class.getField("MOUSE_WHEEL_EVENT_MASK").getLong(null);
			MOUSE_WHEEL = <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.class.getField("MOUSE_WHEEL").getInt(null);
		} catch (Exception exc) { /* not 1.4 */ }
	}
	{
		if (MOUSE_WHEEL != 0) { // disable global focus-manager for this component in 1.4
			try {
				getClass().getMethod("setFocusTraversalKeysEnabled", new Class[] { Boolean.TYPE }).
					<error descr="Cannot resolve method 'invoke(Thinlet, java.lang.Object[])'">invoke</error>(this, new Object[] { Boolean.FALSE });
			} catch (Exception exc) { /* never */ }
		}
		enableEvents(<error descr="Cannot resolve symbol 'AWTEvent'">AWTEvent</error>.COMPONENT_EVENT_MASK |
			<error descr="Cannot resolve symbol 'AWTEvent'">AWTEvent</error>.FOCUS_EVENT_MASK | <error descr="Cannot resolve symbol 'AWTEvent'">AWTEvent</error>.KEY_EVENT_MASK |
			<error descr="Cannot resolve symbol 'AWTEvent'">AWTEvent</error>.MOUSE_EVENT_MASK | <error descr="Cannot resolve symbol 'AWTEvent'">AWTEvent</error>.MOUSE_MOTION_EVENT_MASK | WHEEL_MASK);
	}
	//<java

	/**
	 *
	 */
	public void setColors(int background, int text, int textbackground,
			int border, int disable, int hover, int press,
			int focus, int select) {
		c_bg = new <error descr="Cannot resolve symbol 'Color'">Color</error>(background); c_text = new <error descr="Cannot resolve symbol 'Color'">Color</error>(text);
		c_textbg = new <error descr="Cannot resolve symbol 'Color'">Color</error>(textbackground); c_border = new <error descr="Cannot resolve symbol 'Color'">Color</error>(border);
		c_disable = new <error descr="Cannot resolve symbol 'Color'">Color</error>(disable); c_hover = new <error descr="Cannot resolve symbol 'Color'">Color</error>(hover);
		c_press = new <error descr="Cannot resolve symbol 'Color'">Color</error>(press); c_focus = new <error descr="Cannot resolve symbol 'Color'">Color</error>(focus);
		c_select = new <error descr="Cannot resolve symbol 'Color'">Color</error>(select);
		//midp c_ctrl = c_hover;
		//java>
		int[] pix = new int[block * block];
		int r1 = c_bg.<error descr="Cannot resolve method 'getRed()'">getRed</error>(); int r2 = c_press.<error descr="Cannot resolve method 'getRed()'">getRed</error>();
		int g1 = c_bg.<error descr="Cannot resolve method 'getGreen()'">getGreen</error>(); int g2 = c_press.<error descr="Cannot resolve method 'getGreen()'">getGreen</error>();
		int b1 = c_bg.<error descr="Cannot resolve method 'getBlue()'">getBlue</error>(); int b2 = c_press.<error descr="Cannot resolve method 'getBlue()'">getBlue</error>();
		for (int i = 0; i < block; i++) {
			int r = r1 - (r1 - r2) * i / block;
			int g = g1 - (g1 - g2) * i / block;
			int b = b1 - (b1 - b2) * i / block;
			int color = (255 << 24) | (r << 16) | (g << 8) | b;
			for (int j = 0; j < block; j++) {
				pix[i * block + j] = color;
				//pix[j * block + i] = color;
			}
		}
		gradient = createImage(new <error descr="Cannot resolve symbol 'MemoryImageSource'">MemoryImageSource</error>(block, block, pix, 0, block));
		//<java
	}

	/**
	 *
	 */
	public void setFont(<error descr="Cannot resolve symbol 'Font'">Font</error> font) {
		block = getFontMetrics(font).<error descr="Cannot resolve method 'getHeight()'">getHeight</error>(); //java
		super.setFont(font); //java
		//midp block = font.getHeight();
		//midp this.font = font;
	}

	/**
	 *
	 */
	private void doLayout(Object component) {
		String classname = getClass(component);
		if ("combobox" == classname) {
			if (getBoolean(component, "editable", true)) {
				<error descr="Cannot resolve symbol 'Image'">Image</error> icon = getIcon(component, "icon", null);
				layoutField(component, block, false,
					(icon != null) ? icon.<error descr="Cannot resolve method 'getWidth(Thinlet)'">getWidth</error>(this) : 0);
			} // set editable -> validate (overwrite textfield repaint)
			else {
				int selected = getInteger(component, "selected", -1);
				if (selected != -1) {
					Object choice = getItem(component, "choice", selected);
					set(component, "text", get(choice, "text"));
					set(component, "icon", get(choice, "icon"));
				}
			}
		}
		else if (("textfield" == classname) || ("passwordfield" == classname)) {
			layoutField(component, 0, ("passwordfield" == classname), 0);
		}
		else if ("textarea" == classname) {
			String text = getString(component, "text", "");
			int start = getInteger(component, "start", 0);
			if (start > text.length()) { setInteger(component, "start", start = text.length(), 0); }
			int end = getInteger(component, "end", 0);
			if (end > text.length()) { setInteger(component, "end", end = text.length(), 0); }
			int caretx = 0; int carety = 0;
			<error descr="Cannot resolve symbol 'FontMetrics'">FontMetrics</error> fm = getFontMetrics(getFont()); //java
			int width = 0, height = 0;
			for (int i = 0, j = 0; j != -1; i = j + 1) {
				j = text.indexOf('\n', i);
				if (i != j) { // && i != text.length()
					String line = (j != -1) ? text.substring(i, j) : text.substring(i); //java
					width = Math.max(width, fm.<error descr="Cannot resolve method 'stringWidth(java.lang.String)'">stringWidth</error>(line)); //java
					//midp width = font.substringWidth(text, i, ((j != -1) ? j : text.length()) - i);
				}
				if ((end >= i) && ((j == -1) || (end <= j))) {
					caretx = fm.<error descr="Cannot resolve method 'stringWidth(java.lang.String)'">stringWidth</error>(text.substring(i, end)); //java
					//midp caretx = font.substringWidth(text, i, end - i);
					carety = height;
				}
				height += fm.<error descr="Cannot resolve method 'getHeight()'">getHeight</error>();
			}
			layoutScrollPane(component, width + 2,
				height - fm.<error descr="Cannot resolve method 'getLeading()'">getLeading</error>() + 2, 0, 0);
			scrollToVisible(component, caretx, carety,
				2, fm.<error descr="Cannot resolve method 'getAscent()'">getAscent</error>() + fm.<error descr="Cannot resolve method 'getDescent()'">getDescent</error>() + 2);
		}
		else if ("tabbedpane" == classname) {
			<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> bounds = getRectangle(component, "bounds");
			String placement = getString(component, "placement", "top");
			boolean horizontal = ((placement == "top") || (placement == "bottom"));
			int tabd = 0;
			int tabsize = 0;
			for (Object comp = get(component, "tab");
					comp != null; comp = get(comp, ":next")) {
				Dimension d = getSize(comp, 8, 4, "left");
				setRectangle(comp, "bounds",
					horizontal ? tabd : 0, horizontal ? 0 : tabd, d.width, d.height);
				tabd += horizontal ? d.width : d.height;
				tabsize = Math.max(tabsize, horizontal ? d.height : d.width);
			}
			for (Object comp = get(component, "tab");
					comp != null; comp = get(comp, ":next")) {
				<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> r = getRectangle(comp, "bounds");
				if (horizontal) {
					if (placement == "bottom") { r.<error descr="Cannot resolve symbol 'y'">y</error> = bounds.<error descr="Cannot resolve symbol 'height'">height</error> - tabsize; }
					r.<error descr="Cannot resolve symbol 'height'">height</error> = tabsize;
				} else {
					if (placement == "right") { r.<error descr="Cannot resolve symbol 'x'">x</error> = bounds.<error descr="Cannot resolve symbol 'width'">width</error> - tabsize; }
					r.<error descr="Cannot resolve symbol 'width'">width</error> = tabsize;
				}
			}
			int cx = (placement == "left") ? (tabsize + 1) : 2;
			int cy = (placement == "top") ? (tabsize + 1) : 2;
			int cwidth = bounds.<error descr="Cannot resolve symbol 'width'">width</error> - (horizontal ? 4 : (tabsize + 3));
			int cheight = bounds.<error descr="Cannot resolve symbol 'height'">height</error> - (horizontal ? (tabsize + 3) : 4);
			for (Object comp = get(component, "component");
					comp != null; comp = get(comp, ":next")) {
				if (!getBoolean(comp, "visible", true)) { continue; }
				setRectangle(comp, "bounds", cx, cy, cwidth, cheight);
				doLayout(comp);
			}
		}
		else if (("panel" == classname) || (classname == "dialog")) {
			int gap = getInteger(component, "gap", 0);
			int[][] grid = getGrid(component, gap);
			if (grid != null) {
				int top = getInteger(component, "top", 0);
				int left = getInteger(component, "left", 0);
				int bottom = getInteger(component, "bottom", 0);
				int right = getInteger(component, "right", 0);
				if (classname == "dialog") {
					int titleheight = getInteger(component, "titleheight", 0);
					top += 4 + titleheight; left += 4; bottom += 4; right += 4;
				}

				<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> bounds = getRectangle(component, "bounds");
				for (int i = 0; i < 2; i++) {
					int d = ((i == 0) ? (bounds.<error descr="Cannot resolve symbol 'width'">width</error> - left - right) :
						(bounds.<error descr="Cannot resolve symbol 'height'">height</error> - top - bottom)) -
						getSum(grid[i], 0, grid[i].length, gap, false);
					if (d != 0) {
						int w = getSum(grid[2 + i], 0, grid[2 + i].length, 0, false);
						if (w > 0) {
							for (int j = 0; j < grid[i].length; j++) {
								if (grid[2 + i][j] != 0) {
									grid[i][j] += d * grid[2 + i][j] / w;
								}
							}
						}
					}
				}

				int i = 0;
				for (Object comp = get(component, "component");
						comp != null; comp = get(comp, ":next")) {
					if (!getBoolean(comp, "visible", true)) { continue; }
					int ix = left + getSum(grid[0], 0, grid[4][i], gap, true);
					int iy = top + getSum(grid[1], 0, grid[5][i], gap, true);
					int iwidth = getSum(grid[0], grid[4][i], grid[6][i], gap, false);
					int iheight = getSum(grid[1], grid[5][i], grid[7][i], gap, false);
					String halign = getString(comp, "halign", "fill");
					String valign = getString(comp, "valign", "fill");
					if ((halign != "fill") || (valign != "fill")) {
						Dimension d = getPreferredSize(comp);
						if (halign != "fill") {
							int dw = Math.max(0, iwidth - d.width);
							if (halign == "center") { ix += dw / 2; }
								else if (halign == "right") { ix += dw; }
							iwidth -= dw;
						}
						if (valign != "fill") {
							int dh = Math.max(0, iheight - d.height);
							if (valign == "center") { iy += dh / 2; }
								else if (valign == "bottom") { iy += dh; }
							iheight -= dh;
						}
					}
					setRectangle(comp, "bounds", ix, iy, iwidth, iheight);
					doLayout(comp);
					i++;
				}
			}
		}
		else if ("desktop" == classname) {
			<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> bounds = getRectangle(component, "bounds");
			for (Object comp = get(component, "component");
					comp != null; comp = get(comp, ":next")) {
				String iclass = getClass(comp);
				if (iclass == "dialog") {
					Dimension d = getPreferredSize(comp);
					if (get(comp, "bounds") == null)
					setRectangle(comp, "bounds",
						Math.max(0, (bounds.<error descr="Cannot resolve symbol 'width'">width</error> - d.width) / 2),
						Math.max(0, (bounds.<error descr="Cannot resolve symbol 'height'">height</error> - d.height) / 2),
						Math.min(d.width, bounds.<error descr="Cannot resolve symbol 'width'">width</error>), Math.min(d.height, bounds.<error descr="Cannot resolve symbol 'height'">height</error>));
				} else if ((iclass == "combolist") || (iclass == "popupmenu")) {
						iclass = iclass; //compiler bug
				} else {
					setRectangle(comp, "bounds", 0, 0, bounds.<error descr="Cannot resolve symbol 'width'">width</error>, bounds.<error descr="Cannot resolve symbol 'height'">height</error>);
				}
				doLayout(comp);
			}
		}
		else if ("spinbox" == classname) {
			layoutField(component, block, false, 0);
		}
		else if ("splitpane" == classname) {
			<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> bounds = getRectangle(component, "bounds");
			boolean horizontal = ("vertical" != get(component, "orientation"));
			int divider = getInteger(component, "divider", -1);
			int maxdiv = (horizontal ? bounds.<error descr="Cannot resolve symbol 'width'">width</error> : bounds.<error descr="Cannot resolve symbol 'height'">height</error>) - 5;

			Object comp1 = get(component, "component");
			boolean visible1 = (comp1 != null) && getBoolean(comp1, "visible", true);
			if (divider == -1) {
				int d1 = 0;
				if (visible1) {
					Dimension d = getPreferredSize(comp1);
					d1 = horizontal ? d.width : d.height;
				}
				divider = Math.min(d1, maxdiv);
				setInteger(component, "divider", divider, -1);
			}
			else if (divider > maxdiv) {
				setInteger(component, "divider", divider = maxdiv, -1);
			}

			if (visible1) {
				setRectangle(comp1, "bounds", 0, 0, horizontal ? divider : bounds.<error descr="Cannot resolve symbol 'width'">width</error>,
					horizontal ? bounds.<error descr="Cannot resolve symbol 'height'">height</error> : divider);
				doLayout(comp1);
			}
			Object comp2 = (comp1 != null) ? get(comp1, ":next") : null;
			if ((comp2 != null) && getBoolean(comp2, "visible", true)) {
				setRectangle(comp2, "bounds", horizontal ? (divider + 5) : 0,
					horizontal ? 0 : (divider + 5),
					horizontal ? (bounds.<error descr="Cannot resolve symbol 'width'">width</error> - 5 - divider) : bounds.<error descr="Cannot resolve symbol 'width'">width</error>,
					horizontal ? bounds.<error descr="Cannot resolve symbol 'height'">height</error> : (bounds.<error descr="Cannot resolve symbol 'height'">height</error> - 5 - divider));
				doLayout(comp2);
			}
		}
		else if (("list" == classname) ||
				("table" == classname) || ("tree" == classname)) {
			int width = 0;
			int columnheight = 0;
			if ("table" == classname) {
				for (Object column = get(component, "column");
						column != null; column = get(column, ":next")) {
					width += getInteger(column, "width", 80);
					Dimension d = getSize(column, 2, 2, "left");
					columnheight = Math.max(columnheight, d.height);
				}
			}
			String itemname = ("list" == classname) ? "item" :
				(("table" == classname) ? "row" : "node");
			int y = 0;
			int level = 0;
			for (Object item = get(component, itemname); item != null;) {
				int x = 0;
				int iwidth = 0; int iheight = 0;
				if ("table" == classname) {
					iwidth = width;
					for (Object cell = get(item, "cell");
							cell != null; cell = get(cell, ":next")) {
						Dimension d = getSize(cell, 2, 3, "left");
						iheight = Math.max(iheight, d.height);
					}
				}
				else {
					if ("tree" == classname) {
						x = (level + 1) * block;
					}
					Dimension d = getSize(item, 2, 3, "left");
					iwidth = d.width; iheight = d.height;
					width = Math.max(width, x + d.width);
				}
				setRectangle(item, "bounds", x, y, iwidth, iheight);
				y += iheight;
				if ("tree" == classname) {
					Object next = get(item, "node");
					if ((next != null) && getBoolean(item, "expanded", true)) {
						level++;
					} else {
						while (((next = get(item, ":next")) == null) && (level > 0)) {
							item = getParent(item);
							level--;
						}
					}
					item = next;
				} else {
					item = get(item, ":next");
				}
			}
			layoutScrollPane(component, width, y - 1, 0, columnheight);
		}
		else if ("menubar" == classname) {
			<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> bounds = getRectangle(component, "bounds");
			int x = 0;
			for (Object menu = get(component, "menu");
					menu != null; menu = get(menu, ":next")) {
				Dimension d = getSize(menu, 8, 4, "left");
				setRectangle(menu, "bounds", x, 0, d.width, bounds.<error descr="Cannot resolve symbol 'height'">height</error>);
				x += d.width;
			}
		}
		else if (("combolist" == classname) || ("popupmenu" == classname)) {
			boolean combo = ("combolist" == classname);
			int pw = 0; int ph = 0; int pxy = combo ? 0 : 1;
			for (Object item = get(get(component, combo ? "combobox" : "menu"),
					combo ? "choice" : "menu"); item != null; item = get(item, ":next")) {
				String itemclass = combo ? null : getClass(item);
				Dimension d = (itemclass == "separator") ? new Dimension(1, 1) :
					getSize(item, 8 , 4, "left");
				if (itemclass == "checkboxmenuitem") {
					d.width = d.width + block + 3;
					d.height = Math.max(block, d.height);
				}
				else if (itemclass == "menu") {
					d.width += block;
				}
				setRectangle(item, "bounds", pxy, pxy + ph, d.width, d.height);
				pw = Math.max(pw, d.width);
				ph += d.height;
			}
			<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> r = getRectangle(component, "bounds");
			r.<error descr="Cannot resolve symbol 'width'">width</error> = pw + 2; r.<error descr="Cannot resolve symbol 'height'">height</error> = ph + 2;
			if (combo) {
				<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> db = getRectangle(content, "bounds");
				if (r.<error descr="Cannot resolve symbol 'y'">y</error> + ph + 2 > db.<error descr="Cannot resolve symbol 'height'">height</error>) {
					r.<error descr="Cannot resolve symbol 'width'">width</error> = pw + 2 + block;
					r.<error descr="Cannot resolve symbol 'height'">height</error> = db.<error descr="Cannot resolve symbol 'height'">height</error> - r.<error descr="Cannot resolve symbol 'y'">y</error>;
				}
				else {
					r.<error descr="Cannot resolve symbol 'height'">height</error> = Math.min(r.<error descr="Cannot resolve symbol 'height'">height</error>, db.<error descr="Cannot resolve symbol 'height'">height</error> - r.<error descr="Cannot resolve symbol 'y'">y</error>);
				}
				r.<error descr="Cannot resolve symbol 'width'">width</error> = Math.min(r.<error descr="Cannot resolve symbol 'width'">width</error>, db.<error descr="Cannot resolve symbol 'width'">width</error> - r.<error descr="Cannot resolve symbol 'x'">x</error>);
				layoutScrollPane(component, pw, ph, 0, 0);//~
			}
		}
		//java>
		else if ("bean" == classname) {
				<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> r = getRectangle(component, "bounds");
				((Component) get(component, "bean")).setBounds(r);
		}
		//<java
	}

	/**
	 *
	 */
	private Object popup(Object component, Object classname) {
		Object popup = null;
		int px = 0; int py = 0;
		if (("menubar" == classname) || ("popupmenu" == classname)) {
			Object popupmenu = get(component, "popupmenu");
			Object selected = get(component, "selected");
			if (popupmenu != null) {
				if (get(popupmenu, "menu") == selected) { return null; }
				set(popupmenu, "selected", null);
				set(popupmenu, "menu", null);
				removeItemImpl(content, "component", popupmenu);
				repaint(popupmenu);
				set(popupmenu, ":parent", null);
				set(component, "popupmenu", null);
				if (mouseinside == popupmenu) {
					findComponent(content, mousex, mousey);
					handleMouseEvent(mousex, mousex, 1, false, false, false, //java
						<error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_ENTERED, mouseinside, insidepart); //java
				}
				popup(popupmenu, "popupmenu");
			}
			if ((selected == null) || (getClass(selected) != "menu")) { return null; }
			popup = createImpl("popupmenu");
			set(popup, "menu", selected);
			set(component, "popupmenu", popup);

			<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> bounds = getRectangle(selected, "bounds");
			if ("menubar" == classname) {
				px = bounds.<error descr="Cannot resolve symbol 'x'">x</error>; py = bounds.<error descr="Cannot resolve symbol 'y'">y</error> + bounds.<error descr="Cannot resolve symbol 'height'">height</error> - 1;
			} else {
				px = bounds.<error descr="Cannot resolve symbol 'x'">x</error> + getRectangle(component, "bounds").<error descr="Cannot resolve symbol 'width'">width</error> - 4;
				py = bounds.<error descr="Cannot resolve symbol 'y'">y</error>;
			}
		}
		else { //if ("combobox" == classname) {
			popup = createImpl("combolist");
			set(popup, "combobox", component);
			set(component, "combolist", popup);

			py = getRectangle(component, "bounds").<error descr="Cannot resolve symbol 'height'">height</error> + 1;
		}
		if (("menubar" == classname) || ("combobox" == classname)) {
			popupowner = component;
		}
		insertItem(content, "component", popup, 0);
		set(popup, ":parent", content);
		while (component != content) {
			<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> r = getRectangle(component, "bounds");
			px += r.<error descr="Cannot resolve symbol 'x'">x</error>; py += r.<error descr="Cannot resolve symbol 'y'">y</error>;
			component = getParent(component);
		}
		setRectangle(popup, "bounds", px, py, 0, 0);
		doLayout(popup); repaint(popup);
		return popup;
	}

	/**
	 *
	 */
	private void closeup(Object combobox, Object combolist, Object item) {
		if ((item != null) && getBoolean(item, "enabled", true)) {
			String text = getString(item, "text", "");
			set(combobox, "text", text); // if editable
			setInteger(combobox, "start", text.length(), 0);
			setInteger(combobox, "end", 0, 0);
			set(combobox, "icon", get(item, "icon"));
			validate(combobox);
			setInteger(combobox, "selected", getIndex(combobox, "choice", item), -1);
			invoke(combobox, "action");
		}
		set(combolist, "combobox", null);
		set(combobox, "combolist", null);
		removeItemImpl(content, "component", combolist);
		repaint(combolist);
		set(combolist, ":parent", null);
		popupowner = null;
		if (mouseinside == combolist) {
			findComponent(content, mousex, mousey);
			handleMouseEvent(mousex, mousex, 1, false, false, false, //java
				<error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_ENTERED, mouseinside, insidepart); //java
		}
	}

	/**
	 *
	 */
	private void closeup(Object menubar) {
		set(menubar, "selected", null);
		popup(menubar, "menubar");
		repaint(menubar); // , selected
		popupowner = null;
	}

	/**
	 *
	 */
	private void closeup() {
		if (popupowner != null) {
			String classname = getClass(popupowner);
			if ("menubar" == classname) {
				closeup(popupowner);
			}
			else if ("combobox" == classname) {
				closeup(popupowner, get(popupowner, "combolist"), null);
			}
		}
	}

	/**
	 *
	 */
	private void showTip() {
		String text = null;
		tooltipowner = null;
		String classname = getClass(mouseinside);
		if ((classname == "tabbedpane") || (classname == "menubar") || (classname == "popupmenu")) {
			if (insidepart != null) {
				text = getString(insidepart, "tooltip", null);
			}
		}
		else if (classname == "combolist") {
			if (insidepart instanceof Object[]) {
				text = getString(insidepart, "tooltip", null);
			}
		}
		//list table tree
		if (text == null) { text = getString(mouseinside, "tooltip", null); }
			else { tooltipowner = insidepart; }
		if (text != null) {
			<error descr="Cannot resolve symbol 'FontMetrics'">FontMetrics</error> fm = getFontMetrics(getFont());
			int width = fm.<error descr="Cannot resolve method 'stringWidth(java.lang.String)'">stringWidth</error>(text) + 4;
			int height = fm.<error descr="Cannot resolve method 'getAscent()'">getAscent</error>() + fm.<error descr="Cannot resolve method 'getDescent()'">getDescent</error>() + 4;
			if (tooltipowner == null) { tooltipowner = mouseinside; }
			setRectangle(tooltipowner, "tooltipbounds", mousex + 10, mousey + 10, width, height);
			repaint(mousex + 10, mousey + 10, width, height);
		}
	}

	/**
	 *
	 */
	private void hideTip() {
		if (tooltipowner != null) {
			<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> bounds = getRectangle(tooltipowner, "tooltipbounds");
			set(tooltipowner, "tooltipbounds", null);
			tooltipowner = null;
			repaint(bounds.<error descr="Cannot resolve symbol 'x'">x</error>, bounds.<error descr="Cannot resolve symbol 'y'">y</error>, bounds.<error descr="Cannot resolve symbol 'width'">width</error>, bounds.<error descr="Cannot resolve symbol 'height'">height</error>);
		}
	}

	/**
	 *
	 */
	private void layoutField(Object component, int dw, boolean hidden, int left) {
		int width = getRectangle(component, "bounds").<error descr="Cannot resolve symbol 'width'">width</error> - left -dw;
		String text = getString(component, "text", "");
		int start = getInteger(component, "start", 0);
		if (start > text.length()) { setInteger(component, "start", start = text.length(), 0); }
		int end = getInteger(component, "end", 0);
		if (end > text.length()) { setInteger(component, "end", end = text.length(), 0); }
		int offset = getInteger(component, "offset", 0);
		int off = offset;
		<error descr="Cannot resolve symbol 'FontMetrics'">FontMetrics</error> fm = getFontMetrics(getFont());
		int caret = hidden ? (fm.<error descr="Cannot resolve method 'charWidth(char)'">charWidth</error>('*') * end) :
			fm.<error descr="Cannot resolve method 'stringWidth(java.lang.String)'">stringWidth</error>(text.substring(0, end)); //java
			//midp font.substringWidth(text, 0, end);
		if (off > caret) {
			off = caret;
		}
		else if (off < caret - width + 4) {
			off = caret - width + 4;
		}
		off = Math.max(0, Math.min(off, (hidden ? (fm.<error descr="Cannot resolve method 'charWidth(char)'">charWidth</error>('*') *
			text.length()) : fm.<error descr="Cannot resolve method 'stringWidth(java.lang.String)'">stringWidth</error>(text)) - width + 4));
		if (off != offset) {
			setInteger(component, "offset", off, 0);
		}
	}

	/**
	 *
	 */
	private void layoutScrollPane(Object component,
			int contentwidth, int contentheight, int rowwidth, int columnheight) {
		<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> bounds = getRectangle(component, "bounds");
		boolean hneed = false; boolean vneed = false;
		if (contentwidth > bounds.<error descr="Cannot resolve symbol 'width'">width</error> - rowwidth - 2) {
			hneed = true;
			vneed = (contentheight > bounds.<error descr="Cannot resolve symbol 'height'">height</error> - columnheight - 2 - block);
		}
		if (vneed || (contentheight > bounds.<error descr="Cannot resolve symbol 'height'">height</error> - columnheight - 2)) {
			vneed = true;
			hneed = hneed || (contentwidth > bounds.<error descr="Cannot resolve symbol 'width'">width</error> - rowwidth - 2 - block);
		}
		int viewportwidth = bounds.<error descr="Cannot resolve symbol 'width'">width</error> - rowwidth - (vneed ? block : 0);
		int viewportheight = bounds.<error descr="Cannot resolve symbol 'height'">height</error> - columnheight - (hneed ? block : 0);
		setRectangle(component, ":port",
			rowwidth, columnheight, viewportwidth, viewportheight); //?rowwidth

		<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> view = getRectangle(component, ":view");
		setRectangle(component, ":view",
			(view != null) ? Math.max(0,
				Math.min(view.<error descr="Cannot resolve symbol 'x'">x</error>, contentwidth - viewportwidth + 2)) : 0,
			(view != null) ? Math.max(0,
				Math.min(view.<error descr="Cannot resolve symbol 'y'">y</error>, contentheight - viewportheight + 2)) : 0,
			Math.max(viewportwidth - 2, contentwidth),
			Math.max(viewportheight - 2, contentheight));
	}

	/**
	 *
	 */
	private void scrollToVisible(Object component,
			int x, int y, int width, int height) {
		<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> view = getRectangle(component, ":view");
		<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> port = getRectangle(component, ":port");
		int vx = Math.max(x + width - port.<error descr="Cannot resolve symbol 'width'">width</error> + 2, Math.min(view.<error descr="Cannot resolve symbol 'x'">x</error>, x));
		int vy = Math.max(y + height - port.<error descr="Cannot resolve symbol 'height'">height</error> + 2, Math.min(view.<error descr="Cannot resolve symbol 'y'">y</error>, y));
		if ((view.<error descr="Cannot resolve symbol 'x'">x</error> != vx) || (view.<error descr="Cannot resolve symbol 'y'">y</error> != vy)) {
			repaint(component); // horizontal | vertical
			view.<error descr="Cannot resolve symbol 'x'">x</error> = vx; view.<error descr="Cannot resolve symbol 'y'">y</error> = vy;
		}
	}

	/**
	 *
	 */
	public Dimension getPreferredSize() {
		return getPreferredSize(content);
	}

	/**
	 *
	 */
	private Dimension getPreferredSize(Object component) {
		int width = getInteger(component, "width", 0);
		int height = getInteger(component, "height", 0);
		if ((width > 0) && (height > 0)) {
			return new Dimension(width, height);
		}
		String classname = getClass(component);
		//System.out.println("classname: " + classname);
		if ("label" == classname) {
			return getSize(component, 0, 0, "left");
		}
		if ("button" == classname) {
			return getSize(component, 12, 6, "center");
		}
		if ("checkbox" == classname) {
			Dimension d = getSize(component, 0, 0, "left");
			d.width = d.width + block + 3;
			d.height = Math.max(block, d.height);
			return d;
		}
		if ("combobox" == classname) {
			if (getBoolean(component, "editable", true)) {
				Dimension size = getFieldSize(component);
				<error descr="Cannot resolve symbol 'Image'">Image</error> icon = getIcon(component, "icon", null);
				if (icon != null) {
					size.width += icon.<error descr="Cannot resolve method 'getWidth(Thinlet)'">getWidth</error>(this);
					size.height = Math.max(size.height, icon.<error descr="Cannot resolve method 'getHeight(Thinlet)'">getHeight</error>(this) + 2);
				}
				size.width += block;
				return size;
			} else {
				int selected = getInteger(component, "selected", -1);
				return getSize((selected != -1) ?
					getItemImpl(component, "choice", selected) :
					get(component, "choice"), 4 + block, 4, "left");
			}
		}
		if (("textfield" == classname) || ("passwordfield" == classname)) {
			return getFieldSize(component);
		}
		if ("textarea" == classname) {
			int columns = getInteger(component, "columns", 0);
			int rows = getInteger(component, "rows", 0); // 'e' -> 'm' ?
			<error descr="Cannot resolve symbol 'FontMetrics'">FontMetrics</error> fm = getFontMetrics(getFont()); //java
			return new Dimension(
				((columns > 0) ? (columns * fm.<error descr="Cannot resolve method 'charWidth(char)'">charWidth</error>('e') + 2) : 76) + 2 + block,
				((rows > 0) ? (rows * fm.<error descr="Cannot resolve method 'getHeight()'">getHeight</error>() - fm.<error descr="Cannot resolve method 'getLeading()'">getLeading</error>() + 2) : 76) + 2 + block);
		}
		if ("tabbedpane" == classname) {
			String placement = getString(component, "placement", "top");
			boolean horizontal = ((placement == "top") || (placement == "bottom"));
			int tabsize = 0;
			int contentwidth = 0; int contentheight = 0;
			for (Object comp = get(component, "tab");
					comp != null; comp = get(comp, ":next")) {
				Dimension d = getSize(comp, 8, 4, "left");
				tabsize = Math.max(tabsize, horizontal ? d.height : d.width);
			}
			for (Object comp = get(component, "component");
					comp != null; comp = get(comp, ":next")) {
				if (!getBoolean(comp, "visible", true)) { continue; }
				Dimension d = getPreferredSize(comp);
				contentwidth = Math.max(contentwidth, d.width);
				contentheight = Math.max(contentheight, d.height);
			}
			return new Dimension(contentwidth + (horizontal ? 4 : (tabsize + 3)),
				contentheight + (horizontal ? (tabsize + 3) : 4));
		}
		if (("panel" == classname) || (classname == "dialog")) {
			Dimension size = new Dimension(
				getInteger(component, "left", 0) + getInteger(component, "right", 0),
				getInteger(component, "top", 0) + getInteger(component, "bottom", 0));
			if (classname == "dialog") {
				int titleheight = getSize(component, 0, 0, "left").height;
				setInteger(component, "titleheight", titleheight, 0);
				size.width += 8; size.height += 8 + titleheight;
			}
			int gap = getInteger(component, "gap", 0);
			int[][] grid = getGrid(component, gap);
			if (grid != null) {
				size.width += getSum(grid[0], 0, grid[0].length, gap, false);
				size.height += getSum(grid[1], 0, grid[1].length, gap, false);
			}
			return size;
		}
		else if ("desktop" == classname) {
			Dimension size = new Dimension();
			for (Object comp = get(component, "component");
					comp != null; comp = get(comp, ":next")) {
				String iclass = getClass(comp);
				if ((iclass != "dialog") && (iclass != "popupmenu") &&
						(iclass != "combolist")) {
					Dimension d = getPreferredSize(comp);
					size.width = Math.max(d.width, size.width);
					size.height = Math.max(d.height, size.height);
				}
			}
			return size;
		}
		if ("spinbox" == classname) {
			Dimension size = getFieldSize(component);
			size.width += block;
			return size;
		}
		if ("progressbar" == classname) {
			boolean horizontal = ("vertical" != get(component, "orientation"));
			return new Dimension(horizontal ? 76 : 6, horizontal ? 6 : 76);
		}
		if ("slider" == classname) {
			boolean horizontal = ("vertical" != get(component, "orientation"));
			return new Dimension(horizontal ? 76 : 10, horizontal ? 10 : 76);
		}
		if ("splitpane" == classname) {
			boolean horizontal = ("vertical" != get(component, "orientation"));
			Object comp1 = get(component, "component");
			Dimension size = ((comp1 == null) || !getBoolean(comp1, "visible", true)) ?
				new Dimension() : getPreferredSize(comp1);
			Object comp2 = get(comp1, ":next");
			if ((comp2 != null) && getBoolean(comp2, "visible", true)) {
				Dimension d = getPreferredSize(comp2);
				size.width = horizontal ? (size.width + d.width) :
					Math.max(size.width, d.width);
				size.height = horizontal ? Math.max(size.height, d.height) :
					(size.height + d.height);
			}
			if (horizontal) { size.width += 5; } else { size.height += 5; }
			return size;
		}
		if (("list" == classname) ||
				("table" == classname) || ("tree" == classname)) {
			return new Dimension(76 + 2 + block, 76 + 2 + block);
		}
		if ("separator" == classname) {
			return new Dimension(1, 1);
		}
		if ("menubar" == classname) {
			Dimension size = new Dimension(0, 0);
			for (Object menu = get(component, "menu");
					menu != null; menu = get(menu, ":next")) {
				Dimension d = getSize(menu, 8, 4, "left");
				size.width += d.width;
				size.height = Math.max(size.height, d.height);
			}
			return size;
		}
		//java>
		if ("bean" == classname) {
				return ((Component) get(component, "bean")).getPreferredSize();
		}
		//<java
		throw new IllegalArgumentException((String) classname);
	}

	/**
	 *
	 */
	private int[][] getGrid(Object component, int gap) {
		int count = 0;
		for (Object comp = get(component, "component"); comp != null;
				comp = get(comp, ":next")) {
			if (getBoolean(comp, "visible", true)) { count++; }
		}
		if (count == 0) { return null; }
		int columns = getInteger(component, "columns", 0);
		int icols = (columns != 0) ? columns : count;
		int irows = (columns != 0) ? ((count + columns - 1) / columns) : 1;
		int[][] grid = {
			new int[icols], new int[irows], // columnwidths, rowheights
			new int[icols], new int[irows], // columnweights, rowweights
			new int[count], new int[count], // gridx, gridy
			new int[count], new int[count] }; // gridwidth, gridheight
		int[] columnheight = new int[icols];
		int[][] cache = null; // preferredwidth, height, columnweight, rowweight

		int i = 0; int x = 0; int y = 0;
		int nextsize = 0;
		for (Object comp = get(component, "component");
				comp != null; comp = get(comp, ":next")) {
			if (!getBoolean(comp, "visible", true)) { continue; }
			int colspan = ((columns != 0) && (columns < count)) ?
				Math.min(getInteger(comp, "colspan", 1), columns) : 1;
			int rowspan = (columns != 1) ? getInteger(comp, "rowspan", 1) : 1;

			for (int j = 0; j < colspan; j++) {
				if ((columns != 0) && (x + colspan > columns)) {
					x = 0; y++; j = -1;
				}
				else if (columnheight[x + j] > y) {
					x += (j + 1); j = -1;
				}
			}
			if (y + rowspan > grid[1].length) {
				int[] rowheights = new int[y + rowspan];
				System.arraycopy(grid[1], 0, rowheights, 0, grid[1].length);
				grid[1] = rowheights;
				int[] rowweights = new int[y + rowspan];
				System.arraycopy(grid[3], 0, rowweights, 0, grid[3].length);
				grid[3] = rowweights;
			}
			for (int j = 0; j < colspan; j++) {
				columnheight[x + j] = y + rowspan;
			}

			int weightx = getInteger(comp, "weightx", 0);
			int weighty = getInteger(comp, "weighty", 0);
			Dimension d = getPreferredSize(comp);

			if (colspan == 1) {
				grid[0][x] = Math.max(grid[0][x], d.width); // columnwidths
				grid[2][x] = Math.max(grid[2][x], weightx); // columnweights
			}
			else {
				if (cache == null) { cache = new int[4][count]; }
				cache[0][i] = d.width;
				cache[2][i] = weightx;
				if ((nextsize == 0) || (colspan < nextsize)) { nextsize = colspan; }
			}
			if (rowspan == 1) {
				grid[1][y] = Math.max(grid[1][y], d.height); // rowheights
				grid[3][y] = Math.max(grid[3][y], weighty); // rowweights
			}
			else {
				if (cache == null) { cache = new int[4][count]; }
				cache[1][i] = d.height;
				cache[3][i] = weighty;
				if ((nextsize == 0) || (rowspan < nextsize)) { nextsize = rowspan; }
			}
			grid[4][i] = x; //gridx
			grid[5][i] = y; //gridy
			grid[6][i] = colspan; //gridwidth
			grid[7][i] = rowspan; //gridheight

			x += colspan;
			i++;
		}

		while (nextsize != 0) {
			int size = nextsize; nextsize = 0;
			for (int j = 0; j < 2; j++) { // horizontal, vertical
				for (int k = 0; k < count; k++) {
					if (grid[6 + j][k] == size) { // gridwidth, gridheight
						int gridpoint = grid[4 + j][k]; // gridx, gridy

						int weightdiff = cache[2 + j][k];
						for (int m = 0; (weightdiff > 0) && (m < size); m++) {
							weightdiff -= grid[2 + j][gridpoint + m];
						}
						if (weightdiff > 0) {
							int weightsum = cache[2 + j][k] - weightdiff;
							for (int m = 0; (weightsum > 0) && (m < size); m++) {
								int weight = grid[2 + j][gridpoint + m];
								if (weight > 0) {
									int weightinc = weight * weightdiff / weightsum;
									grid[2 + j][gridpoint + m] += weightinc;
									weightdiff -= weightinc;
									weightsum -= weightinc;
								}
							}
							grid[2 + j][gridpoint + size - 1] += weightdiff;
						}

						int sizediff = cache[j][k];
						int weightsum = 0;
						for (int m = 0; (sizediff > 0) && (m < size); m++) {
							sizediff -= grid[j][gridpoint + m];
							weightsum += grid[2 + j][gridpoint + m];
						}
						if (sizediff > 0) {
							for (int m = 0; (weightsum > 0) && (m < size); m++) {
								int weight = grid[2 + j][gridpoint + m];
								if (weight > 0) {
									int sizeinc = weight * sizediff / weightsum;
									grid[j][gridpoint + m] += sizeinc;
									sizediff -= sizeinc;
									weightsum -= weight;
								}
							}
							grid[j][gridpoint + size - 1] += sizediff;
						}
					}
					else if ((grid[6 + j][k] > size) &&
							((nextsize == 0) || (grid[6 + j][k] < nextsize))) {
						nextsize = grid[6 + j][k];
					}
				}
			}
		}
		return grid;
	}

	/**
	 *
	 */
	private int getSum(int[] values,
			int from, int length, int gap, boolean last) {
		if (length <= 0) { return 0; }
		int value = 0;
		for (int i = 0; i < length; i++) {
			value += values[from + i];
		}
		return value + (length - (last ? 0 : 1)) * gap;
	}

	/**
	 *
	 */
	private Dimension getFieldSize(Object component) {
		String text = getString(component, "text", "");
		int columns = getInteger(component, "columns", 0);
		<error descr="Cannot resolve symbol 'FontMetrics'">FontMetrics</error> fm = getFontMetrics(getFont());
		return new Dimension(((columns > 0) ?
			(columns * fm.<error descr="Cannot resolve method 'charWidth(char)'">charWidth</error>('e')) : 76) + 4,
			fm.<error descr="Cannot resolve method 'getAscent()'">getAscent</error>() + fm.<error descr="Cannot resolve method 'getDescent()'">getDescent</error>() + 4); // fm.stringWidth(text)
	}

	/**
	 *
	 */
	private Dimension getSize(Object component,
			int dx, int dy, String defaultalignment) {
		String text = getString(component, "text", null);
		int tw = 0; int th = 0;
		if (text != null) {
			<error descr="Cannot resolve symbol 'FontMetrics'">FontMetrics</error> fm = getFontMetrics(getFont());
			tw = fm.<error descr="Cannot resolve method 'stringWidth(java.lang.String)'">stringWidth</error>(text);
			th = fm.<error descr="Cannot resolve method 'getAscent()'">getAscent</error>() + fm.<error descr="Cannot resolve method 'getDescent()'">getDescent</error>();
		}
		<error descr="Cannot resolve symbol 'Image'">Image</error> icon = getIcon(component, "icon", null);
		int iw = 0; int ih = 0;
		if (icon != null) {
			iw = icon.<error descr="Cannot resolve method 'getWidth(Thinlet)'">getWidth</error>(this);
			ih = icon.<error descr="Cannot resolve method 'getHeight(Thinlet)'">getHeight</error>(this);
		}
		return new Dimension(tw + iw + dx, Math.max(th, ih) + dy);
	}
	//java>

	/**
	 *
	 */
	public void update(<error descr="Cannot resolve symbol 'Graphics'">Graphics</error> g) {
		paint(g);
	}

	/**
	 *~
	 */
	public boolean imageUpdate(<error descr="Cannot resolve symbol 'Image'">Image</error> img, int infoflags, int x, int y, int width, int height) {
		if (infoflags == <error descr="Cannot resolve symbol 'ImageObserver'">ImageObserver</error>.ALLBITS) {
			validate(content);
			return super.imageUpdate(img, infoflags, x, y, width, height);
		}
		return true;
	}

	/**
	 *
	 */
	public void paint(<error descr="Cannot resolve symbol 'Graphics'">Graphics</error> g) {
		//g.setColor(Color.orange);
		//g.fillRect(0, 0, getSize().width, getSize().height);
		//long time = System.currentTimeMillis();
		<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> clip = g.<error descr="Cannot resolve method 'getClipBounds()'">getClipBounds</error>();
		///dg.setClip(r.x, r.y, r.width, r.height);
		paint(g, clip.<error descr="Cannot resolve symbol 'x'">x</error>, clip.<error descr="Cannot resolve symbol 'y'">y</error>, clip.<error descr="Cannot resolve symbol 'width'">width</error>, clip.<error descr="Cannot resolve symbol 'height'">height</error>, content, isEnabled());
		//System.out.println(System.currentTimeMillis() - time);
		///g.setClip(0, 0, getSize().width, getSize().height);
		//g.setColor(Color.red); g.drawRect(clip.x, clip.y, clip.width - 1, clip.height - 1);
	}

	//<java
	/*midp
	protected void paint(Graphics g) {
		paint(g, g.getClipX(), g.getClipY(),
			g.getClipWidth(), g.getClipHeight(), content, true);
	}

	protected void showNotify() {
		setRectangle(content, "bounds", 0, 0, getWidth(), getHeight());
		doLayout(content);
	}
	midp*/

	/**
	 *
	 */
	private void paint(<error descr="Cannot resolve symbol 'Graphics'">Graphics</error> g,
			int clipx, int clipy, int clipwidth, int clipheight,
			Object component, boolean enabled) {
		if (!getBoolean(component, "visible", true)) { return; }
		<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> bounds = getRectangle(component, "bounds");
		if (bounds == null) { return; }
		if (bounds.<error descr="Cannot resolve symbol 'width'">width</error> < 0) {
			bounds.<error descr="Cannot resolve symbol 'width'">width</error> = Math.abs(bounds.<error descr="Cannot resolve symbol 'width'">width</error>);
			doLayout(component);
		}
		if ((clipx + clipwidth < bounds.<error descr="Cannot resolve symbol 'x'">x</error>) ||
				(clipx > bounds.<error descr="Cannot resolve symbol 'x'">x</error> + bounds.<error descr="Cannot resolve symbol 'width'">width</error>) ||
				(clipy + clipheight < bounds.<error descr="Cannot resolve symbol 'y'">y</error>) ||
				(clipy > bounds.<error descr="Cannot resolve symbol 'y'">y</error> + bounds.<error descr="Cannot resolve symbol 'height'">height</error>)) {
			return;
		}
		clipx -= bounds.<error descr="Cannot resolve symbol 'x'">x</error>; clipy -= bounds.<error descr="Cannot resolve symbol 'y'">y</error>;
		String classname = getClass(component);
		boolean pressed = (mousepressed == component);
		boolean inside = (mouseinside == component) &&
			((mousepressed == null) || pressed);
		boolean focus = focusinside && (focusowner == component);
		enabled = getBoolean(component, "enabled", true); //enabled &&
		g.<error descr="Cannot resolve method 'translate(?, ?)'">translate</error>(bounds.<error descr="Cannot resolve symbol 'x'">x</error>, bounds.<error descr="Cannot resolve symbol 'y'">y</error>);
		//g.setClip(0, 0, bounds.width, bounds.height);

		if ("label" == classname) {
			paintContent(component, g, clipx, clipy, clipwidth, clipheight,
				0, 0, bounds.<error descr="Cannot resolve symbol 'width'">width</error>, bounds.<error descr="Cannot resolve symbol 'height'">height</error>,
				enabled ? c_text : c_disable, "left", true);
		}
		else if ("button" == classname) {
			paintRect(g, 0, 0, bounds.<error descr="Cannot resolve symbol 'width'">width</error>, bounds.<error descr="Cannot resolve symbol 'height'">height</error>,
				enabled ? c_border : c_disable,
				enabled ? ((inside != pressed) ? c_hover :
					(pressed ? c_press : c_ctrl)) : c_bg, true, true, true, true);
			if (focus) {
				g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(c_focus);
				g.<error descr="Cannot resolve method 'drawRect(int, int, ?, ?)'">drawRect</error>(2, 2, bounds.<error descr="Cannot resolve symbol 'width'">width</error> - 5, bounds.<error descr="Cannot resolve symbol 'height'">height</error> - 5);
			}
			paintContent(component, g, clipx, clipy, clipwidth, clipheight,
				6, 3, bounds.<error descr="Cannot resolve symbol 'width'">width</error> - 12, bounds.<error descr="Cannot resolve symbol 'height'">height</error> - 6,
				enabled ? c_text : c_disable, "center", true);
		}
		else if ("checkbox" == classname) {
			boolean selected = getBoolean(component, "selected", false);
			String group = getString(component, "group", null);
			<error descr="Cannot resolve symbol 'Color'">Color</error> border = enabled ? c_border : c_disable;
			<error descr="Cannot resolve symbol 'Color'">Color</error> foreground = enabled ? ((inside != pressed) ? c_hover :
				(pressed ? c_press : c_ctrl)) : c_bg;
			int dy = (bounds.<error descr="Cannot resolve symbol 'height'">height</error> - block + 2) / 2;
			if (group == null) {
				paintRect(g, 1, dy + 1, block - 2, block - 2,
					border, foreground, true, true, true, true);
			} else {
				g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>((foreground != c_ctrl) ? foreground : c_bg);
				g.<error descr="Cannot resolve method 'fillOval(int, int, int, int)'">fillOval</error>(1, dy + 1, block - 3, block - 3); //java
				g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(border);
				g.<error descr="Cannot resolve method 'drawOval(int, int, int, int)'">drawOval</error>(1, dy + 1, block - 3, block - 3); //java
			}
			if (focus) {
				g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(c_focus);
				if (group == null) {
					g.<error descr="Cannot resolve method 'drawRect(int, int, int, int)'">drawRect</error>(3, dy + 3, block - 7, block - 7);
				} else {
					g.<error descr="Cannot resolve method 'drawOval(int, int, int, int)'">drawOval</error>(3, dy + 3, block - 7, block - 7); //java
				}
			}
			if((!selected && inside && pressed) ||
					(selected && (!inside || !pressed))) {
				g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(enabled ? c_text : c_disable);
				if (group == null) {
					g.<error descr="Cannot resolve method 'fillRect(int, int, int, int)'">fillRect</error>(3, dy + block - 9, 2, 6);
					g.<error descr="Cannot resolve method 'drawLine(int, int, int, int)'">drawLine</error>(3, dy + block - 4, block - 4, dy + 3);
					g.<error descr="Cannot resolve method 'drawLine(int, int, int, int)'">drawLine</error>(4, dy + block - 4, block - 4, dy + 4);
				} else {
					g.<error descr="Cannot resolve method 'fillOval(int, int, int, int)'">fillOval</error>(5, dy + 5, block - 10, block - 10); //java
					g.<error descr="Cannot resolve method 'drawOval(int, int, int, int)'">drawOval</error>(4, dy + 4, block - 9, block - 9); //java
				}
			}
			paintContent(component, g, clipx, clipy, clipwidth, clipheight,
				block + 3, 0, bounds.<error descr="Cannot resolve symbol 'width'">width</error> - block - 3, bounds.<error descr="Cannot resolve symbol 'height'">height</error>,
				enabled ? c_text : c_disable, "left", true);
		}
		else if ("combobox" == classname) {
			if (getBoolean(component, "editable", true)) {
				<error descr="Cannot resolve symbol 'Image'">Image</error> icon = getIcon(component, "icon", null);
				int left = (icon != null) ? icon.<error descr="Cannot resolve method 'getWidth(Thinlet)'">getWidth</error>(this) : 0;
				paintField(g, clipx, clipy, clipwidth, clipheight, component,
					bounds.<error descr="Cannot resolve symbol 'width'">width</error> - block, bounds.<error descr="Cannot resolve symbol 'height'">height</error>,
					inside, pressed, focus, enabled, false, left);
				if (icon != null) {
					g.<error descr="Cannot resolve method 'drawImage(Image, int, ?, Thinlet)'">drawImage</error>(icon, 2, (bounds.<error descr="Cannot resolve symbol 'height'">height</error> - icon.<error descr="Cannot resolve method 'getHeight(Thinlet)'">getHeight</error>(this)) / 2, this); //java
					//midp g.drawImage(icon, 2, bounds.height / 2, Graphics.LEFT | Graphics.VCENTER);
				}
				paintArrow(g, bounds.<error descr="Cannot resolve symbol 'width'">width</error> - block, 0, block, bounds.<error descr="Cannot resolve symbol 'height'">height</error>,
					'S', enabled, inside, pressed, "down", true, false, true, true);
			} else {
				paintRect(g, 0, 0, bounds.<error descr="Cannot resolve symbol 'width'">width</error>, bounds.<error descr="Cannot resolve symbol 'height'">height</error>,
					enabled ? c_border : c_disable,
					enabled ? ((inside != pressed) ? c_hover :
						(pressed ? c_press : c_ctrl)) : c_bg, true, true, true, true);
				paintContent(component, g, clipx, clipy, clipwidth, clipheight,
					2, 2, bounds.<error descr="Cannot resolve symbol 'width'">width</error> - block - 4, bounds.<error descr="Cannot resolve symbol 'height'">height</error> - 4,
					enabled ? c_text : c_disable, "left", false);
				paintArrow(g, bounds.<error descr="Cannot resolve symbol 'width'">width</error> - block, 0, block, bounds.<error descr="Cannot resolve symbol 'height'">height</error>, 'S');
				if (focus) {
					g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(c_focus);
					g.<error descr="Cannot resolve method 'drawRect(int, int, ?, ?)'">drawRect</error>(2, 2, bounds.<error descr="Cannot resolve symbol 'width'">width</error> - block - 5, bounds.<error descr="Cannot resolve symbol 'height'">height</error> - 5);
				}
			}
		}
		else if ("combolist" == classname) {
			<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> view = getRectangle(component, ":view");
			<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> viewport = getRectangle(component, ":port");
			g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(c_border);
			g.<error descr="Cannot resolve method 'drawRect(?, ?, ?, ?)'">drawRect</error>(viewport.<error descr="Cannot resolve symbol 'x'">x</error>, viewport.<error descr="Cannot resolve symbol 'y'">y</error>, viewport.<error descr="Cannot resolve symbol 'width'">width</error> - 1, viewport.<error descr="Cannot resolve symbol 'height'">height</error> - 1);
			if (paintScrollPane(g, clipx, clipy, clipwidth, clipheight,
					bounds, view, viewport, enabled, inside, pressed)) {
				Object selected = get(component, "inside");
				int ly = clipy - viewport.<error descr="Cannot resolve symbol 'y'">y</error> - 1;
				int yfrom = view.<error descr="Cannot resolve symbol 'y'">y</error> + Math.max(0, ly);
				int yto = view.<error descr="Cannot resolve symbol 'y'">y</error> + Math.min(viewport.<error descr="Cannot resolve symbol 'height'">height</error> - 2, ly + clipheight);
				for (Object choice = get(get(component, "combobox"), "choice");
						choice != null; choice = get(choice, ":next")) {
					<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> r = getRectangle(choice, "bounds");
					if (yto <= r.<error descr="Cannot resolve symbol 'y'">y</error>) { break; }
					if (yfrom >= r.<error descr="Cannot resolve symbol 'y'">y</error> + r.<error descr="Cannot resolve symbol 'height'">height</error>) { continue; }
					boolean armed = (selected == choice);
					paintRect(g, r.<error descr="Cannot resolve symbol 'x'">x</error>, r.<error descr="Cannot resolve symbol 'y'">y</error>, bounds.<error descr="Cannot resolve symbol 'width'">width</error> - 2, r.<error descr="Cannot resolve symbol 'height'">height</error>, c_border,
						armed ? c_select : c_bg, false, false, false, false);
					paintContent(choice, g, clipx, yfrom, clipwidth, yto - yfrom,
						r.<error descr="Cannot resolve symbol 'x'">x</error> + 4, r.<error descr="Cannot resolve symbol 'y'">y</error> + 2, bounds.<error descr="Cannot resolve symbol 'width'">width</error> - 10, r.<error descr="Cannot resolve symbol 'height'">height</error> - 4,
						getBoolean(choice, "enabled", true) ? c_text : c_disable, "left", false);
				}
				resetScrollPane(g, clipx, clipy, clipwidth, clipheight, view, viewport);
			}
			//paintRect(g, 0, 0, bounds.width, bounds.height,
			//	secondary1, c_ctrl, true, true, true, true);
		}
		else if (("textfield" == classname) || ("passwordfield" == classname)) {
			paintField(g, clipx, clipy, clipwidth, clipheight, component,
				bounds.<error descr="Cannot resolve symbol 'width'">width</error>, bounds.<error descr="Cannot resolve symbol 'height'">height</error>,
				inside, pressed, focus, enabled, ("passwordfield" == classname), 0);
		}
		else if ("textarea" == classname) {
			<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> view = getRectangle(component, ":view");
			<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> viewport = getRectangle(component, ":port");
			boolean editable = getBoolean(component, "editable", true);
			paintRect(g, viewport.<error descr="Cannot resolve symbol 'x'">x</error>, viewport.<error descr="Cannot resolve symbol 'y'">y</error>, viewport.<error descr="Cannot resolve symbol 'width'">width</error>, viewport.<error descr="Cannot resolve symbol 'height'">height</error>,
				enabled ? c_border : c_disable, editable ? c_textbg : c_bg,
				true, true, true, true);
			if (paintScrollPane(g, clipx, clipy, clipwidth, clipheight,
					bounds, view, viewport, enabled, inside, pressed)) {
				String text = getString(component, "text", "");
				int start = focus ? getInteger(component, "start", 0) : 0;
				int end = focus ? getInteger(component, "end", 0) : 0;
				int is = Math.min(start, end); int ie = Math.max(start, end);
				boolean wrap = getBoolean(component, "wrap", false);
				<error descr="Cannot resolve symbol 'FontMetrics'">FontMetrics</error> fm = g.<error descr="Cannot resolve method 'getFontMetrics()'">getFontMetrics</error>(); //java
				int fontascent = fm.<error descr="Cannot resolve method 'getAscent()'">getAscent</error>(); int fontheight = fm.<error descr="Cannot resolve method 'getHeight()'">getHeight</error>(); //java
				//midp int fontheight = fm.getHeight();
				int ascent = 1;
				int ly = clipy - viewport.<error descr="Cannot resolve symbol 'y'">y</error> - 1;
				int yfrom = view.<error descr="Cannot resolve symbol 'y'">y</error> + Math.max(0, ly);
				int yto = view.<error descr="Cannot resolve symbol 'y'">y</error> + Math.min(viewport.<error descr="Cannot resolve symbol 'height'">height</error> - 2, ly + clipheight);
				//g.setColor(Color.pink); g.fillRect(0, yfrom - 1, 75, 2); g.fillRect(0, yto - 1, 75, 2);

				boolean prevletter = false; int n = text.length(); char c = 0;
				for (int i = 0, j = -1, k = 0; k <= n; k++) { // j is the last space index (before k)
					if (yto <= ascent) { break; }
					if (wrap) {
						if (((k == n) || ((c = text.charAt(k)) == '\n') || (c == ' ')) &&
								(j  > i) && (fm.<error descr="Cannot resolve method 'stringWidth(java.lang.String)'">stringWidth</error>(text.substring(i, k)) > viewport.<error descr="Cannot resolve symbol 'width'">width</error> - 4)) {
							k--; // draw line to the begin of the current word (+ spaces) if it is out of width
						}
						else if ((k == n) || (c == '\n')) { // draw line to the text/line end
							j = k; prevletter = false;
						}
						else {
							if ((c == ' ') && (prevletter || (j > i))) { j = k; } // keep spaces starting the line
							prevletter = (c != ' ');
							continue;
						}
					}
					else {
						if ((k == n) || ((c = text.charAt(k)) == '\n')) { j = k; } else { continue; }
					}
					if (yfrom < ascent + fontheight) {
						String line = (j != -1) ? text.substring(i, j) : text.substring(i); //java
						if (focus && (is != ie) && (ie >= i) && ((j == -1) || (is <= j))) {
							int xs = (is < i) ? -1 : (((j != -1) && (is > j)) ? (view.<error descr="Cannot resolve symbol 'width'">width</error> - 1) :
								fm.<error descr="Cannot resolve method 'stringWidth(java.lang.String)'">stringWidth</error>(text.substring(i, is))); //java
								//midp font.substringWidth(text, i, is - i));
							int xe = ((j != -1) && (ie > j)) ? (view.<error descr="Cannot resolve symbol 'width'">width</error> - 1) :
								fm.<error descr="Cannot resolve method 'stringWidth(java.lang.String)'">stringWidth</error>(text.substring(i, ie)); //java
								//midp font.substringWidth(text, i, ie - i);
							g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(c_select);
							g.<error descr="Cannot resolve method 'fillRect(int, int, int, int)'">fillRect</error>(1 + xs, ascent, xe - xs, fontheight);
						}
						g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(enabled ? c_text : c_disable);
						g.<error descr="Cannot resolve method 'drawString(java.lang.String, int, int)'">drawString</error>(line, 1, ascent + fontascent); //java
						//midp g.drawSubstring(text, i, ((j != -1) ? j : text.length()) - i, 1, ascent, Graphics.LEFT | Graphics.TOP);
						if (focus && (end >= i) && ((j == -1) || (end <= j))) {
							int caret = fm.<error descr="Cannot resolve method 'stringWidth(java.lang.String)'">stringWidth</error>(text.substring(i, end)); //java
							//midp int caret = font.substringWidth(text, i, end - i);
							g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(c_focus);
							g.<error descr="Cannot resolve method 'fillRect(int, int, int, int)'">fillRect</error>(caret, ascent, 1, fontheight);
						}
					}
					ascent += fontheight;
					i = j + 1;
				}
				resetScrollPane(g, clipx, clipy, clipwidth, clipheight, view, viewport);
			}
		}
		else if ("tabbedpane" == classname) {
			int i = 0; <error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> last = null;
			int selected = getInteger(component, "selected", 0);
			String placement = getString(component, "placement", "top");
			for (Object comp = get(component, "tab");
					comp != null; comp = get(comp, ":next")) {
				<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> r = getRectangle(comp, "bounds");
				boolean hover = !(selected == i) && inside &&
					(mousepressed == null) && (insidepart == comp);
				boolean sel = (selected == i);
				boolean tabenabled = enabled && getBoolean(comp, "enabled", true);
				paintRect(g, r.<error descr="Cannot resolve symbol 'x'">x</error>, r.<error descr="Cannot resolve symbol 'y'">y</error>, r.<error descr="Cannot resolve symbol 'width'">width</error>, r.<error descr="Cannot resolve symbol 'height'">height</error>,
					enabled ? c_border : c_disable,
					tabenabled ? (sel ? c_bg : (hover ? c_hover : c_ctrl)) : c_ctrl,
					(placement != "bottom") || !sel, (placement != "right") || !sel,
					(placement == "bottom") || ((placement == "top") && !sel),
					(placement == "right") || ((placement == "left") && !sel));
				if (focus && sel) {
					g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(c_focus);
					g.<error descr="Cannot resolve method 'drawRect(?, ?, ?, ?)'">drawRect</error>(r.<error descr="Cannot resolve symbol 'x'">x</error> + 2, r.<error descr="Cannot resolve symbol 'y'">y</error> + 2, r.<error descr="Cannot resolve symbol 'width'">width</error> - 4, r.<error descr="Cannot resolve symbol 'height'">height</error> - 4);
				}
				paintContent(comp, g, clipx, clipy, clipwidth, clipheight,
					r.<error descr="Cannot resolve symbol 'x'">x</error> + 4, r.<error descr="Cannot resolve symbol 'y'">y</error> + 2, r.<error descr="Cannot resolve symbol 'width'">width</error> - 8, r.<error descr="Cannot resolve symbol 'height'">height</error> - 4,
					tabenabled ? c_text : c_disable, "left", true);
				i++; last = r;
			}
			if (last != null) {
				boolean horizontal = ((placement == "top") || (placement == "bottom"));
				paintRect(g, horizontal ? (last.<error descr="Cannot resolve symbol 'x'">x</error> + last.<error descr="Cannot resolve symbol 'width'">width</error>) : last.<error descr="Cannot resolve symbol 'x'">x</error>,
					horizontal ? last.<error descr="Cannot resolve symbol 'y'">y</error> : (last.<error descr="Cannot resolve symbol 'y'">y</error> + last.<error descr="Cannot resolve symbol 'height'">height</error>),
					horizontal ? (bounds.<error descr="Cannot resolve symbol 'width'">width</error> - last.<error descr="Cannot resolve symbol 'x'">x</error> - last.<error descr="Cannot resolve symbol 'width'">width</error>) : last.<error descr="Cannot resolve symbol 'width'">width</error>,
					horizontal ? last.<error descr="Cannot resolve symbol 'height'">height</error> : (bounds.<error descr="Cannot resolve symbol 'height'">height</error> - last.<error descr="Cannot resolve symbol 'y'">y</error> - last.<error descr="Cannot resolve symbol 'height'">height</error>),
					enabled ? c_border : c_disable, c_bg,
					(placement != "top"), (placement != "left"),
					(placement == "top"), (placement == "left"));
				paintRect(g, (placement == "left") ? last.<error descr="Cannot resolve symbol 'width'">width</error> : 0,
					(placement == "top") ? last.<error descr="Cannot resolve symbol 'height'">height</error> : 0,
					horizontal ? bounds.<error descr="Cannot resolve symbol 'width'">width</error> : (bounds.<error descr="Cannot resolve symbol 'width'">width</error> - last.<error descr="Cannot resolve symbol 'width'">width</error>),
					horizontal ? (bounds.<error descr="Cannot resolve symbol 'height'">height</error> - last.<error descr="Cannot resolve symbol 'height'">height</error>) : bounds.<error descr="Cannot resolve symbol 'height'">height</error>,
					enabled ? c_border : c_disable, c_bg,
					(placement != "top"), (placement != "left"),
					(placement != "bottom"), (placement != "right"));
			}
			Object tabcontent = getItemImpl(component, "component", selected);
			if (tabcontent != null) {
				paint(g, clipx, clipy, clipwidth, clipheight, tabcontent, enabled);
			}
		}
		else if (("panel" == classname) || ("dialog" == classname)) {
			if ("dialog" == classname) {
				int titleheight = getInteger(component, "titleheight", 0);
				paintRect(g, 0, 0, bounds.<error descr="Cannot resolve symbol 'width'">width</error>, 3 + titleheight,
					c_border, c_ctrl, true, true, false, true);
				paintRect(g, 0, 3 + titleheight, bounds.<error descr="Cannot resolve symbol 'width'">width</error>, bounds.<error descr="Cannot resolve symbol 'height'">height</error> - 3 - titleheight,
					c_border, c_press, false, true, true, true);
				paintContent(component, g, clipx, clipy, clipwidth, clipheight,
					3, 2, bounds.<error descr="Cannot resolve symbol 'width'">width</error> - 6, titleheight, c_text, "left", false);
				paintRect(g, 3, 3 + titleheight, bounds.<error descr="Cannot resolve symbol 'width'">width</error> - 6, bounds.<error descr="Cannot resolve symbol 'height'">height</error> - 6 - titleheight,
					c_border, c_bg, true, true, true, true);
			} else {
				paintRect(g, 0, 0, bounds.<error descr="Cannot resolve symbol 'width'">width</error>, bounds.<error descr="Cannot resolve symbol 'height'">height</error>,
					c_border, c_bg, false, false, false, false);
			}
			for (Object comp = get(component, "component");
					comp != null; comp = get(comp, ":next")) {
				paint(g, clipx, clipy, clipwidth, clipheight, comp, enabled);
			}
		}
		else if ("desktop" == classname) {
			paintReverse(g, clipx, clipy, clipwidth, clipheight,
				get(component, "component"), enabled);
			//g.setColor(Color.red); if (clip != null) g.drawRect(clipx, clipy, clipwidth, clipheight);
			if (tooltipowner != null) {
				<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> r = getRectangle(tooltipowner, "tooltipbounds");
				paintRect(g, r.<error descr="Cannot resolve symbol 'x'">x</error>, r.<error descr="Cannot resolve symbol 'y'">y</error>, r.<error descr="Cannot resolve symbol 'width'">width</error>, r.<error descr="Cannot resolve symbol 'height'">height</error>,
					c_border, c_bg, true, true, true, true);
				String text = getString(tooltipowner, "tooltip", null);
				g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(c_text);
				g.<error descr="Cannot resolve method 'drawString(java.lang.String, ?, ?)'">drawString</error>(text, r.<error descr="Cannot resolve symbol 'x'">x</error> + 2, r.<error descr="Cannot resolve symbol 'y'">y</error> + g.<error descr="Cannot resolve method 'getFontMetrics()'">getFontMetrics</error>().getAscent() + 2); //java
				//midp g.drawString(text, r.x + 2, r.y + (r.height - font.getHeight()) / 2, Graphics.LEFT | Graphics.TOP);
			}
		}
		else if ("spinbox" == classname) {
			paintField(g, clipx, clipy, clipwidth, clipheight, component,
				bounds.<error descr="Cannot resolve symbol 'width'">width</error> - block, bounds.<error descr="Cannot resolve symbol 'height'">height</error>,
				inside, pressed, focus, enabled, false, 0);
			paintArrow(g, bounds.<error descr="Cannot resolve symbol 'width'">width</error> - block, 0, block, bounds.<error descr="Cannot resolve symbol 'height'">height</error> / 2,
					'N', enabled, inside, pressed, "up", true, false, false, true);
			paintArrow(g, bounds.<error descr="Cannot resolve symbol 'width'">width</error> - block, bounds.<error descr="Cannot resolve symbol 'height'">height</error> / 2,
				block, bounds.<error descr="Cannot resolve symbol 'height'">height</error> - (bounds.<error descr="Cannot resolve symbol 'height'">height</error> / 2),
				'S', enabled, inside, pressed, "down", true, false, true, true);
		}
		else if ("progressbar" == classname) {
			int minimum = getInteger(component, "minimum", 0);
			int maximum = getInteger(component, "maximum", 100);
			int value = getInteger(component, "value", 0);
			boolean horizontal = ("vertical" != get(component, "orientation"));
			int length = (value - minimum) *
				((horizontal ? bounds.<error descr="Cannot resolve symbol 'width'">width</error> : bounds.<error descr="Cannot resolve symbol 'height'">height</error>) - 1) / (maximum - minimum);
			paintRect(g, 0, 0, horizontal ? length : bounds.<error descr="Cannot resolve symbol 'width'">width</error>,
				horizontal ? bounds.<error descr="Cannot resolve symbol 'height'">height</error> : length, enabled ? c_border : c_disable,
				c_select, true, true, horizontal, !horizontal);
			paintRect(g, horizontal ? length : 0, horizontal ? 0 : length,
				horizontal ? (bounds.<error descr="Cannot resolve symbol 'width'">width</error> - length) : bounds.<error descr="Cannot resolve symbol 'width'">width</error>	,
				horizontal ? bounds.<error descr="Cannot resolve symbol 'height'">height</error> : (bounds.<error descr="Cannot resolve symbol 'height'">height</error> - length),
				enabled ? c_border : c_disable, c_bg, true, true, true, true);
		}
		else if ("slider" == classname) {
			int minimum = getInteger(component, "minimum", 0);
			int maximum = getInteger(component, "maximum", 100);
			int value = getInteger(component, "value", 0);
			boolean horizontal = ("vertical" != get(component, "orientation"));
			int length = (value - minimum) *
				((horizontal ? bounds.<error descr="Cannot resolve symbol 'width'">width</error> : bounds.<error descr="Cannot resolve symbol 'height'">height</error>) - block) /
				(maximum - minimum);
			paintRect(g, horizontal ? 0 : 3, horizontal ? 3 : 0,
				horizontal ? length : (bounds.<error descr="Cannot resolve symbol 'width'">width</error> - 6),
				horizontal ? (bounds.<error descr="Cannot resolve symbol 'height'">height</error> - 6) : length,
				enabled ? c_border : c_disable,
				c_bg, true, true, horizontal, !horizontal);
			paintRect(g, horizontal ? length : 0, horizontal ? 0 : length,
				horizontal ? block : bounds.<error descr="Cannot resolve symbol 'width'">width</error>, horizontal ? bounds.<error descr="Cannot resolve symbol 'height'">height</error> : block,
				enabled ? c_border : c_disable,
				enabled ? c_ctrl : c_bg, true, true, true, true);
			if (focus) {
				g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(c_focus);
				g.<error descr="Cannot resolve method 'drawRect(int, int, int, int)'">drawRect</error>(horizontal ? (length + 2) : 2, horizontal ? 2 : (length + 2),
					(horizontal ? block : bounds.<error descr="Cannot resolve symbol 'width'">width</error>) - 5,
					(horizontal ? bounds.<error descr="Cannot resolve symbol 'height'">height</error> : block) - 5);
				//g.drawRect(length + 1, 1, block - 3, bounds.height - 3);
			}
			paintRect(g, horizontal ? (block + length) : 3,
				horizontal ? 3 : (block + length),
				bounds.<error descr="Cannot resolve symbol 'width'">width</error> - (horizontal ? (block + length) : 6),
				bounds.<error descr="Cannot resolve symbol 'height'">height</error> - (horizontal ? 6 : (block + length)),
				enabled ? c_border : c_disable,
				c_bg, horizontal, !horizontal, true, true);
		}
		else if ("splitpane" == classname) {
			boolean horizontal = ("vertical" != get(component, "orientation"));
			int divider = getInteger(component, "divider", -1);
			paintRect(g, horizontal ? divider : 0, horizontal ? 0 : divider,
				horizontal ? 5 : bounds.<error descr="Cannot resolve symbol 'width'">width</error>, horizontal ? bounds.<error descr="Cannot resolve symbol 'height'">height</error> : 5,
				c_border, c_bg, false, false, false, false);
			g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(enabled ? (focus ? c_focus : c_border) : c_disable);
			int xy = horizontal ? bounds.<error descr="Cannot resolve symbol 'height'">height</error> : bounds.<error descr="Cannot resolve symbol 'width'">width</error>;
			int xy1 = Math.max(0, xy / 2 - 12);
			int xy2 = Math.min(xy / 2 + 12, xy - 1);
			for (int i = divider + 1; i < divider + 4; i += 2) {
				if (horizontal) { g.<error descr="Cannot resolve method 'drawLine(int, int, int, int)'">drawLine</error>(i, xy1, i, xy2); }
					else { g.<error descr="Cannot resolve method 'drawLine(int, int, int, int)'">drawLine</error>(xy1, i, xy2, i); }
			}
			Object comp1 = get(component, "component");
			if (comp1 != null) {
				paint(g, clipx, clipy, clipwidth, clipheight, comp1, enabled);
				Object comp2 = get(comp1, ":next");
				if (comp2 != null) {
					paint(g, clipx, clipy, clipwidth, clipheight, comp2, enabled);
				}
			}
		}
		else if (("list" == classname) ||
				("table" == classname) || ("tree" == classname)) {
			<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> view = getRectangle(component, ":view");
			<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> viewport = getRectangle(component, ":port");
			int[] columnwidths = null;
			int lx = clipx - viewport.<error descr="Cannot resolve symbol 'x'">x</error> - 1;
			int xfrom = view.<error descr="Cannot resolve symbol 'x'">x</error> + Math.max(0, lx);
			int xto = view.<error descr="Cannot resolve symbol 'x'">x</error> + Math.min(viewport.<error descr="Cannot resolve symbol 'width'">width</error> - 2, lx + clipwidth);
			if ("table" == classname) {
				columnwidths = new int[getItemCountImpl(component, "column")];
				int i = 0; int x = 0; boolean drawheader = (clipy < viewport.<error descr="Cannot resolve symbol 'y'">y</error>);
				if (drawheader) { g.<error descr="Cannot resolve method 'setClip(?, int, ?, ?)'">setClip</error>(viewport.<error descr="Cannot resolve symbol 'x'">x</error>, 0, viewport.<error descr="Cannot resolve symbol 'width'">width</error>, viewport.<error descr="Cannot resolve symbol 'y'">y</error>); }
				for (Object column = get(component, "column");
						column != null; column = get(column, ":next")) {
					boolean lastcolumn = (i == columnwidths.length - 1);
					int width = getInteger(column, "width", 80);
					if (lastcolumn) { width = Math.max(width, viewport.<error descr="Cannot resolve symbol 'width'">width</error> - x); }
					columnwidths[i] = width;
					if (drawheader && (xfrom < x + width) && (xto > x)) {
						paintRect(g, x - view.<error descr="Cannot resolve symbol 'x'">x</error>, 0, width, viewport.<error descr="Cannot resolve symbol 'y'">y</error>,
							enabled ? c_border : c_disable, enabled ? c_ctrl : c_bg,
							true, true, false, lastcolumn);
						paintContent(column, g, clipx, clipy, clipwidth, clipheight,
							x + 2 - view.<error descr="Cannot resolve symbol 'x'">x</error>, 1, width - 2,
							viewport.<error descr="Cannot resolve symbol 'y'">y</error> - 2, enabled ? c_text : c_disable, "left", false);
					}
					i++; x += width;
				}
				if (drawheader) { g.<error descr="Cannot resolve method 'setClip(int, int, int, int)'">setClip</error>(clipx, clipy, clipwidth, clipheight); }
			}
			paintRect(g, viewport.<error descr="Cannot resolve symbol 'x'">x</error>, viewport.<error descr="Cannot resolve symbol 'y'">y</error>, viewport.<error descr="Cannot resolve symbol 'width'">width</error>, viewport.<error descr="Cannot resolve symbol 'height'">height</error>,
				enabled ? c_border : c_disable, c_textbg, true, true, true, true);
			if (paintScrollPane(g, clipx, clipy, clipwidth, clipheight, bounds,
					view, viewport, enabled, inside, pressed)) {
				Object lead = get(component, "lead");
				int ly = clipy - viewport.<error descr="Cannot resolve symbol 'y'">y</error> - 1;
				int yfrom = view.<error descr="Cannot resolve symbol 'y'">y</error> + Math.max(0, ly);
				int yto = view.<error descr="Cannot resolve symbol 'y'">y</error> + Math.min(viewport.<error descr="Cannot resolve symbol 'height'">height</error> - 2, ly + clipheight);
				for (Object item = get(component, ("list" == classname) ? "item" :
						(("table" == classname) ? "row" : "node")); item != null;) {
					<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> r = getRectangle(item, "bounds");
					if (lead == null) {
						set(component, "lead", lead = item); // draw first item focused when lead is null
					}
					if (yto <= r.<error descr="Cannot resolve symbol 'y'">y</error>) { break; } // the clip bounds are above

					Object next = ("tree" == classname) ? get(item, "node") : null;
					boolean expanded = (next != null) &&
							getBoolean(item, "expanded", true);
					if (yfrom < r.<error descr="Cannot resolve symbol 'y'">y</error> + r.<error descr="Cannot resolve symbol 'height'">height</error>) { // the clip rectangle is not bellow the current item
						boolean selected = getBoolean(item, "selected", false);
						paintRect(g, 0, r.<error descr="Cannot resolve symbol 'y'">y</error>, view.<error descr="Cannot resolve symbol 'width'">width</error>, r.<error descr="Cannot resolve symbol 'height'">height</error>,
							c_bg, selected ? c_select : c_textbg, false, false, true, false);
						if (focus && (lead == item)) {
							g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(c_focus);
							g.<error descr="Cannot resolve method 'drawRect(int, ?, ?, ?)'">drawRect</error>(0, r.<error descr="Cannot resolve symbol 'y'">y</error>, view.<error descr="Cannot resolve symbol 'width'">width</error> - 1, r.<error descr="Cannot resolve symbol 'height'">height</error> - 2);
						}
						if ("table" == classname) {
							int x = 0; int i = 0;
							for (Object cell = get(item, "cell");
									cell != null; cell = get(cell, ":next")) {
								if (xto <= x) { break; }
								int iwidth = (i < columnwidths.length) ? columnwidths[i] : 80;
								if (xfrom < x + iwidth) {
									boolean cellenabled = enabled && getBoolean(cell, "enabled", true);
									paintContent(cell, g, xfrom, yfrom, xto - xfrom, yto - yfrom,
										r.<error descr="Cannot resolve symbol 'x'">x</error> + x + 1, r.<error descr="Cannot resolve symbol 'y'">y</error> + 1, iwidth - 2, r.<error descr="Cannot resolve symbol 'height'">height</error> - 3,
										cellenabled ? c_text : c_disable, "left", false);
								}
								x += iwidth; i++;
							}
						} else {
							boolean itemenabled = enabled && getBoolean(item, "enabled", true);
							paintContent(item, g, xfrom, yfrom, xto - xfrom, yto - yfrom,
								r.<error descr="Cannot resolve symbol 'x'">x</error> + 1, r.<error descr="Cannot resolve symbol 'y'">y</error> + 1, view.<error descr="Cannot resolve symbol 'width'">width</error> - r.<error descr="Cannot resolve symbol 'x'">x</error> - 2,
								r.<error descr="Cannot resolve symbol 'height'">height</error> - 3, itemenabled ? c_text : c_disable, "left", false);

							if (next != null) {
								int x = r.<error descr="Cannot resolve symbol 'x'">x</error> - block / 2;
								int y = r.<error descr="Cannot resolve symbol 'y'">y</error> + (r.<error descr="Cannot resolve symbol 'height'">height</error> - 1) / 2;
								//g.drawRect(x - 4, y - 4, 8, 8);
								paintRect(g, x - 4, y - 4, 9, 9, itemenabled ? c_border : c_disable,
									itemenabled ? c_ctrl : c_bg, true, true, true, true);
								g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(itemenabled ? c_text : c_disable);
								g.<error descr="Cannot resolve method 'drawLine(int, int, int, int)'">drawLine</error>(x - 2, y, x + 2, y);
								if (!expanded) {
									g.<error descr="Cannot resolve method 'drawLine(int, int, int, int)'">drawLine</error>(x, y - 2, x, y + 2);
								}
							}
						}
					}
					if ("tree" == classname) {
						if ((next == null) || !expanded) {
							while ((item != component) && ((next = get(item, ":next")) == null)) {
								item = getParent(item);
							}
						}
						item = next;
					} else {
						item = get(item, ":next");
					}
				}
				/*if (columnwidths != null) {
					g.setColor(c_bg);
					for (int i = 0, cx = -1; i < columnwidths.length - 1; i++) {
						cx += columnwidths[i];
						g.drawLine(cx, 0, cx, view.height);
					}
				}*/
				resetScrollPane(g, clipx, clipy, clipwidth, clipheight, view, viewport);
			}
		}
		else if ("separator" == classname) {
			g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(enabled ? c_border : c_disable);
			g.<error descr="Cannot resolve method 'fillRect(int, int, ?, ?)'">fillRect</error>(0, 0, bounds.<error descr="Cannot resolve symbol 'width'">width</error>, bounds.<error descr="Cannot resolve symbol 'height'">height</error>);
		}
		else if ("menubar" == classname) {
			Object selected = get(component, "selected");
			int lastx = 0;
			for (Object menu = get(component, "menu");
					menu != null; menu = get(menu, ":next")) {
				<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> mb = getRectangle(menu, "bounds");
				if (clipx + clipwidth <= mb.<error descr="Cannot resolve symbol 'x'">x</error>) { break; }
				if (clipx >= mb.<error descr="Cannot resolve symbol 'x'">x</error> + mb.<error descr="Cannot resolve symbol 'width'">width</error>) { continue; }
				boolean armed = (selected == menu);
				boolean hoover = (selected == null) && (insidepart == menu);
				paintRect(g, mb.<error descr="Cannot resolve symbol 'x'">x</error>, 0, mb.<error descr="Cannot resolve symbol 'width'">width</error>, bounds.<error descr="Cannot resolve symbol 'height'">height</error>, enabled ? c_border : c_disable,
					enabled ? (armed ? c_select : (hoover ? c_hover : c_ctrl)) : c_bg,
					armed, armed, true, armed);
				paintContent(menu, g, clipx, clipy, clipwidth, clipheight,
					mb.<error descr="Cannot resolve symbol 'x'">x</error> + 4, 1, mb.<error descr="Cannot resolve symbol 'width'">width</error>, bounds.<error descr="Cannot resolve symbol 'height'">height</error>,
					(enabled && getBoolean(menu, "enabled", true)) ? c_text : c_disable,
					"left", true);
				lastx = mb.<error descr="Cannot resolve symbol 'x'">x</error> + mb.<error descr="Cannot resolve symbol 'width'">width</error>;
			}
			paintRect(g, lastx, 0, bounds.<error descr="Cannot resolve symbol 'width'">width</error> - lastx, bounds.<error descr="Cannot resolve symbol 'height'">height</error>,
				enabled ? c_border : c_disable, enabled ? c_ctrl : c_bg,
				false, false, true, false);
		}
		else if ("popupmenu" == classname) {
			paintRect(g, 0, 0, bounds.<error descr="Cannot resolve symbol 'width'">width</error>, bounds.<error descr="Cannot resolve symbol 'height'">height</error>,
				c_border, c_bg, true, true, true, true);
			Object selected = get(component, "selected");
			for (Object menu = get(get(component, "menu"), "menu");
					menu != null; menu = get(menu, ":next")) {
				<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> r = getRectangle(menu, "bounds");
				if (clipy + clipheight <= r.<error descr="Cannot resolve symbol 'y'">y</error>) { break; }
				if (clipy >= r.<error descr="Cannot resolve symbol 'y'">y</error> + r.<error descr="Cannot resolve symbol 'height'">height</error>) { continue; }
				String itemclass = getClass(menu);
				if (itemclass == "separator") {
					g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(c_border);
					g.<error descr="Cannot resolve method 'fillRect(?, ?, ?, ?)'">fillRect</error>(r.<error descr="Cannot resolve symbol 'x'">x</error>, r.<error descr="Cannot resolve symbol 'y'">y</error>, bounds.<error descr="Cannot resolve symbol 'width'">width</error> - 2, r.<error descr="Cannot resolve symbol 'height'">height</error>);
				} else {
					boolean armed = (selected == menu);
					boolean menuenabled = getBoolean(menu, "enabled", true);
					paintRect(g, r.<error descr="Cannot resolve symbol 'x'">x</error>, r.<error descr="Cannot resolve symbol 'y'">y</error>, bounds.<error descr="Cannot resolve symbol 'width'">width</error> - 2, r.<error descr="Cannot resolve symbol 'height'">height</error>, c_border,
						armed ? c_select : c_bg, false, false, false, false);
					int tx = r.<error descr="Cannot resolve symbol 'x'">x</error>;
					if (itemclass == "checkboxmenuitem") {
						tx += block + 3;
						boolean checked = getBoolean(menu, "selected", false);
						String group = getString(menu, "group", null);
						g.<error descr="Cannot resolve method 'translate(?, ?)'">translate</error>(r.<error descr="Cannot resolve symbol 'x'">x</error> + 4, r.<error descr="Cannot resolve symbol 'y'">y</error> + 2);
						g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(menuenabled ? c_border : c_disable);
						if (group == null) {
							g.<error descr="Cannot resolve method 'drawRect(int, int, int, int)'">drawRect</error>(1, 1, block - 3, block - 3);
						} else {
							g.<error descr="Cannot resolve method 'drawOval(int, int, int, int)'">drawOval</error>(1, 1, block - 3, block - 3); //java
						}
						if (checked) {
							g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(menuenabled ? c_text : c_disable);
							if (group == null) {
								g.<error descr="Cannot resolve method 'fillRect(int, int, int, int)'">fillRect</error>(3, block - 9, 2, 6);
								g.<error descr="Cannot resolve method 'drawLine(int, int, int, int)'">drawLine</error>(3, block - 4, block - 4, 3);
								g.<error descr="Cannot resolve method 'drawLine(int, int, int, int)'">drawLine</error>(4, block - 4, block - 4, 4);
							} else {
								g.<error descr="Cannot resolve method 'fillOval(int, int, int, int)'">fillOval</error>(5, 5, block - 10, block - 10); //java
								g.<error descr="Cannot resolve method 'drawOval(int, int, int, int)'">drawOval</error>(4, 4, block - 9, block - 9); //java
							}
						}
						g.<error descr="Cannot resolve method 'translate(?, ?)'">translate</error>(-r.<error descr="Cannot resolve symbol 'x'">x</error> - 4, -r.<error descr="Cannot resolve symbol 'y'">y</error> - 2);
					}
					paintContent(menu, g, clipx, clipy, clipwidth, clipheight,
						tx + 4, r.<error descr="Cannot resolve symbol 'y'">y</error> + 2, bounds.<error descr="Cannot resolve symbol 'width'">width</error> - 10,
						r.<error descr="Cannot resolve symbol 'height'">height</error> - 4, menuenabled ? c_text : c_disable, "left", true);
					if (itemclass == "menu") {
						paintArrow(g, r.<error descr="Cannot resolve symbol 'x'">x</error> + bounds.<error descr="Cannot resolve symbol 'width'">width</error> - block, r.<error descr="Cannot resolve symbol 'y'">y</error>, block, r.<error descr="Cannot resolve symbol 'height'">height</error>, 'E');
					}
				}
			}
		}
		//java>
		else if ("bean" == classname) {
				g.<error descr="Cannot resolve method 'clipRect(int, int, ?, ?)'">clipRect</error>(0, 0, bounds.<error descr="Cannot resolve symbol 'width'">width</error>, bounds.<error descr="Cannot resolve symbol 'height'">height</error>);
				((Component) get(component, "bean")).paint(g);
				g.<error descr="Cannot resolve method 'setClip(int, int, int, int)'">setClip</error>(clipx, clipy, clipwidth, clipheight);
		}
		//<java
		else throw new IllegalArgumentException((String) classname);
		g.<error descr="Cannot resolve method 'translate(?, ?)'">translate</error>(-bounds.<error descr="Cannot resolve symbol 'x'">x</error>, -bounds.<error descr="Cannot resolve symbol 'y'">y</error>);
		clipx += bounds.<error descr="Cannot resolve symbol 'x'">x</error>; clipy += bounds.<error descr="Cannot resolve symbol 'y'">y</error>;
	}

	/**
	 *
	 */
	private void paintReverse(<error descr="Cannot resolve symbol 'Graphics'">Graphics</error> g,
			int clipx, int clipy, int clipwidth, int clipheight,
			Object component, boolean enabled) {
		if (component != null) {
			<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> bounds = getRectangle(component, "bounds");
			if ((clipx < bounds.<error descr="Cannot resolve symbol 'x'">x</error>) ||
					(clipx + clipwidth > bounds.<error descr="Cannot resolve symbol 'x'">x</error> + bounds.<error descr="Cannot resolve symbol 'width'">width</error>) ||
					(clipy < bounds.<error descr="Cannot resolve symbol 'y'">y</error>) ||
					(clipy + clipheight > bounds.<error descr="Cannot resolve symbol 'y'">y</error> + bounds.<error descr="Cannot resolve symbol 'height'">height</error>)) {
				paintReverse(g, clipx, clipy, clipwidth, clipheight,
					get(component, ":next"), enabled);
			}
			paint(g, clipx, clipy, clipwidth, clipheight, component, enabled);
		}
	}

	/**
	 *
	 */
	private void paintField(<error descr="Cannot resolve symbol 'Graphics'">Graphics</error> g,
			int clipx, int clipy, int clipwidth, int clipheight, Object component,
			int width, int height, boolean inside, boolean pressed,
			boolean focus, boolean enabled, boolean hidden, int left) {
		boolean editable = getBoolean(component, "editable", true);
		paintRect(g, 0, 0, width, height, enabled ? c_border : c_disable,
			editable ? c_textbg : c_bg, true, true, true, true);
		g.<error descr="Cannot resolve method 'clipRect(int, int, int, int)'">clipRect</error>(1 + left, 1, width - left - 2, height - 2);

		String text = getString(component, "text", "");
		int offset = getInteger(component, "offset", 0);
		<error descr="Cannot resolve symbol 'FontMetrics'">FontMetrics</error> fm = g.<error descr="Cannot resolve method 'getFontMetrics()'">getFontMetrics</error>(); //java

		int caret = 0;
		if (focus) {
			int start = getInteger(component, "start", 0);
			int end = getInteger(component, "end", 0);
			caret = hidden ? (fm.<error descr="Cannot resolve method 'charWidth(char)'">charWidth</error>('*') * end) :
				fm.<error descr="Cannot resolve method 'stringWidth(java.lang.String)'">stringWidth</error>(text.substring(0, end)); //java
				//midp font.substringWidth(text, 0, end);
			if (start != end) {
				int is = hidden ? (fm.<error descr="Cannot resolve method 'charWidth(char)'">charWidth</error>('*') * start) :
					fm.<error descr="Cannot resolve method 'stringWidth(java.lang.String)'">stringWidth</error>(text.substring(0, start)); //java
					//midp font.substringWidth(text, 0, start);
				g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(c_select);
				g.<error descr="Cannot resolve method 'fillRect(int, int, int, int)'">fillRect</error>(2 + left - offset + Math.min(is, caret), 1,
					Math.abs(caret - is), height - 2);
			}
		}

		if (focus) {
			g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(c_focus);
			g.<error descr="Cannot resolve method 'fillRect(int, int, int, int)'">fillRect</error>(1 + left - offset + caret, 1, 1, height - 2);
		}

		g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(enabled ? c_text : c_disable);
		int fx = 2 + left - offset;
		int fy = (height + fm.<error descr="Cannot resolve method 'getAscent()'">getAscent</error>() - fm.<error descr="Cannot resolve method 'getDescent()'">getDescent</error>()) / 2; //java
		//midp int fy = (height - font.getHeight()) / 2;
		if (hidden) {
			int fh = fm.<error descr="Cannot resolve method 'charWidth(char)'">charWidth</error>('*');
			for (int i = text.length(); i > 0; i--) {
				g.<error descr="Cannot resolve method 'drawString(java.lang.String, int, int)'">drawString</error>("*", fx, fy); //java
				//midp g.drawChar('*', fx, fy, Graphics.LEFT | Graphics.TOP);
				fx += fh;
			}
		} else {
			g.<error descr="Cannot resolve method 'drawString(java.lang.String, int, int)'">drawString</error>(text, fx, fy); //java
			//midp g.drawString(text, fx, fy, Graphics.LEFT | Graphics.TOP);
		}
		g.<error descr="Cannot resolve method 'setClip(int, int, int, int)'">setClip</error>(clipx, clipy, clipwidth, clipheight);
	}

	/**
	 *
	 */
	private boolean paintScrollPane(<error descr="Cannot resolve symbol 'Graphics'">Graphics</error> g,
			int clipx, int clipy, int clipwidth, int clipheight, <error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> bounds,
			<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> view, <error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> viewport,
			boolean enabled, boolean inside, boolean pressed) {
		if ((viewport.<error descr="Cannot resolve symbol 'y'">y</error> + viewport.<error descr="Cannot resolve symbol 'height'">height</error> < bounds.<error descr="Cannot resolve symbol 'height'">height</error>) &&
				(clipy + clipheight > viewport.<error descr="Cannot resolve symbol 'y'">y</error> + viewport.<error descr="Cannot resolve symbol 'height'">height</error>)) { // need horizontal
			int x = viewport.<error descr="Cannot resolve symbol 'x'">x</error>;
			int y = viewport.<error descr="Cannot resolve symbol 'y'">y</error> + viewport.<error descr="Cannot resolve symbol 'height'">height</error>;
			int height = bounds.<error descr="Cannot resolve symbol 'height'">height</error> - y;
			int button = Math.min(block, viewport.<error descr="Cannot resolve symbol 'width'">width</error> / 2);
			int track = viewport.<error descr="Cannot resolve symbol 'width'">width</error> - (2 * button); //max 10
			int knob = Math.min(track, Math.max(track * (viewport.<error descr="Cannot resolve symbol 'width'">width</error> - 2) / view.<error descr="Cannot resolve symbol 'width'">width</error>, 6));
			int decrease = view.<error descr="Cannot resolve symbol 'x'">x</error> * (track - knob) /
				(view.<error descr="Cannot resolve symbol 'width'">width</error> - viewport.<error descr="Cannot resolve symbol 'width'">width</error> + 2);
			int increase = track - decrease - knob;
			paintArrow(g, x, y, button, height,
				'W', enabled, inside, pressed, "left", false, true, true, false);
			paintRect(g, x + button, y, decrease, height,
				enabled ? c_border : c_disable, c_bg, false, true, true, false);
			paintRect(g, x + button + decrease, y, knob, height,
				enabled ? c_border : c_disable, enabled ? c_ctrl : c_bg, false, true, true, true);
			int n = Math.min(5, (knob - 4) / 3);
			g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(enabled ? c_border : c_disable);
			int cx = (x + button + decrease) + (knob + 2 - n * 3) / 2;
			for (int i = 0; i < n; i++ ) {
				g.<error descr="Cannot resolve method 'drawLine(int, int, int, int)'">drawLine</error>(cx + i * 3, y + 2, cx + i * 3, y + height - 4);
			}
			paintRect(g, x + button + decrease + knob, y, increase, height,
				enabled ? c_border : c_disable, c_bg, false, false, true, true);
			paintArrow(g, x + button + track, y, button, height,
				'E', enabled, inside, pressed, "right", false, false, true, true);
		}
		if ((viewport.<error descr="Cannot resolve symbol 'x'">x</error> + viewport.<error descr="Cannot resolve symbol 'width'">width</error> < bounds.<error descr="Cannot resolve symbol 'width'">width</error>) &&
				(clipx + clipwidth > viewport.<error descr="Cannot resolve symbol 'x'">x</error> + viewport.<error descr="Cannot resolve symbol 'width'">width</error>)) { // need vertical
			int x = viewport.<error descr="Cannot resolve symbol 'x'">x</error> + viewport.<error descr="Cannot resolve symbol 'width'">width</error>;
			int y = viewport.<error descr="Cannot resolve symbol 'y'">y</error>;
			int width = bounds.<error descr="Cannot resolve symbol 'width'">width</error> - x;
			//if (y > 0) {
			//	paintRect(g, x - 1, 0, width + 1, y,
			//		enabled ? c_border : c_disable, c_bg, false, true, false, false);
			//}
			int track = viewport.<error descr="Cannot resolve symbol 'height'">height</error> - (2 * block);
			int knob = track * (viewport.<error descr="Cannot resolve symbol 'height'">height</error> - 2) / view.<error descr="Cannot resolve symbol 'height'">height</error>;
			int decrease = view.<error descr="Cannot resolve symbol 'y'">y</error> * (track - knob) /
				(view.<error descr="Cannot resolve symbol 'height'">height</error> - viewport.<error descr="Cannot resolve symbol 'height'">height</error> + 2);
			int increase = track - decrease - knob;
			paintArrow(g, x, y, width, block,
				'N', enabled, inside, pressed, "up", true, false, false, true);
			paintRect(g, x, y + block, width, decrease,
				enabled ? c_border : c_disable, c_bg, true, false, false, true);
			paintRect(g, x, y + block + decrease, width, knob,
				enabled ? c_border : c_disable, enabled ? c_ctrl : c_bg, true, false, true, true);
			int n = Math.min(5, (knob - 4) / 3);
			g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(enabled ? c_border : c_disable);
			int cy = (y + block + decrease) + (knob + 2 - n * 3) / 2;
			for (int i = 0; i < n; i++ ) {
				g.<error descr="Cannot resolve method 'drawLine(int, int, int, int)'">drawLine</error>(x + 2, cy + i * 3, x + width - 4, cy + i * 3);
			}
			paintRect(g, x, y + block + decrease + knob, width, increase,
				enabled ? c_border : c_disable, c_bg, false, false, true, true);
			paintArrow(g, x, y + block + track, width, block,
			'S', enabled, inside, pressed, "down", false, false, true, true);
		}
		if ((clipx + clipwidth > viewport.<error descr="Cannot resolve symbol 'x'">x</error>) && (clipy + clipheight > viewport.<error descr="Cannot resolve symbol 'y'">y</error>) &&
				(clipx < viewport.<error descr="Cannot resolve symbol 'x'">x</error> + viewport.<error descr="Cannot resolve symbol 'width'">width</error>) && (clipy < viewport.<error descr="Cannot resolve symbol 'y'">y</error> + viewport.<error descr="Cannot resolve symbol 'height'">height</error>)) {
			g.<error descr="Cannot resolve method 'clipRect(?, ?, ?, ?)'">clipRect</error>(viewport.<error descr="Cannot resolve symbol 'x'">x</error> + 1, viewport.<error descr="Cannot resolve symbol 'y'">y</error> + 1, viewport.<error descr="Cannot resolve symbol 'width'">width</error> - 2, viewport.<error descr="Cannot resolve symbol 'height'">height</error> - 2);
			g.<error descr="Cannot resolve method 'translate(?, ?)'">translate</error>(viewport.<error descr="Cannot resolve symbol 'x'">x</error> + 1 - view.<error descr="Cannot resolve symbol 'x'">x</error>, viewport.<error descr="Cannot resolve symbol 'y'">y</error> + 1 - view.<error descr="Cannot resolve symbol 'y'">y</error>);
	 		return true;
		}
		return false;
	}

	/**
	 *
	 */
	private void resetScrollPane(<error descr="Cannot resolve symbol 'Graphics'">Graphics</error> g,
			int clipx, int clipy, int clipwidth, int clipheight,
			<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> view, <error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> viewport) {
		g.<error descr="Cannot resolve method 'translate(?, ?)'">translate</error>(view.<error descr="Cannot resolve symbol 'x'">x</error> - viewport.<error descr="Cannot resolve symbol 'x'">x</error> - 1, view.<error descr="Cannot resolve symbol 'y'">y</error> - viewport.<error descr="Cannot resolve symbol 'y'">y</error> - 1);
		g.<error descr="Cannot resolve method 'setClip(int, int, int, int)'">setClip</error>(clipx, clipy, clipwidth, clipheight);
	}

	/**
	 *
	 */
	private void paintRect(<error descr="Cannot resolve symbol 'Graphics'">Graphics</error> g, int x, int y, int width, int height,
			<error descr="Cannot resolve symbol 'Color'">Color</error> border, <error descr="Cannot resolve symbol 'Color'">Color</error> bg,
			boolean top, boolean left, boolean bottom, boolean right) {
		if ((width <= 0) || (height <= 0)) return;
		g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(border);
		if (top) {
			g.<error descr="Cannot resolve method 'drawLine(int, int, int, int)'">drawLine</error>(x + width - 1, y, x, y);
			y++; height--; if (height <= 0) return;
		}
		if (left) {
			g.<error descr="Cannot resolve method 'drawLine(int, int, int, int)'">drawLine</error>(x, y, x, y + height - 1);
			x++; width--; if (width <= 0) return;
		}
		if (bottom) {
			g.<error descr="Cannot resolve method 'drawLine(int, int, int, int)'">drawLine</error>(x, y + height - 1, x + width - 1, y + height - 1);
			height--; if (height <= 0) return;
		}
		if (right) {
			g.<error descr="Cannot resolve method 'drawLine(int, int, int, int)'">drawLine</error>(x + width - 1, y + height - 1, x + width - 1, y);
			width--; if (width <= 0) return;
		}

		//java>
		if (bg == c_ctrl) {
			if (height > block) {
				g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(c_bg);
				g.<error descr="Cannot resolve method 'fillRect(int, int, int, int)'">fillRect</error>(x, y, width, height - block);
			}
			for (int i = 0; i < width; i += block) {
				g.<error descr="Cannot resolve method 'drawImage(Image, int, int, int, int, int, int, int, int, null)'">drawImage</error>(gradient, x + i, (height > block) ? (y + height - block) : y,
					x + Math.min(i + block, width), y + height,
					0, 0, Math.min(block, width - i), Math.min(block, height), null);
			}
			/*if (width > block) {
				g.setColor(c_bg);
				g.fillRect(x, y, width - block, height);
			}
			for (int i = 0; i < height; i += block) {
				g.drawImage(gradient, (width > block) ? (x + width - block) : x, y + i,
					x + width, y + Math.min(i + block, height),
					0, 0, Math.min(block, width), Math.min(block, height - i), null);
			}*/
		}
		else {
			g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(bg);
			g.<error descr="Cannot resolve method 'fillRect(int, int, int, int)'">fillRect</error>(x, y, width, height);
		}
		//<java
		//midp g.setColor(bg);
		//midp g.fillRect(x, y, width, height);
	}

	/**
	 *
	 */
	private void paintArrow(<error descr="Cannot resolve symbol 'Graphics'">Graphics</error> g, int x, int y, int width, int height,
			char dir, boolean enabled, boolean inside, boolean pressed, String part,
			boolean top, boolean left, boolean bottom, boolean right) {
		inside = inside && (insidepart == part);
		pressed = pressed && (pressedpart == part);
		paintRect(g, x, y, width, height, enabled ? c_border : c_disable,
			enabled ? ((inside != pressed) ? c_hover :
				(pressed ? c_press : c_ctrl)) : c_bg,
			top, left, bottom, right);
		g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(enabled ? c_text : c_disable);
		paintArrow(g, x, y, width, height, dir);
	}

	/**
	 *
	 */
	private void paintArrow(<error descr="Cannot resolve symbol 'Graphics'">Graphics</error> g,
			int x, int y, int width, int height, char dir) {
		int cx = x + width / 2 - 2;
		int cy = y + height / 2 - 2;
		for (int i = 0; i < 4; i++) {
			if (dir == 'N') { // north
				g.<error descr="Cannot resolve method 'drawLine(int, int, int, int)'">drawLine</error>(cx + 1 - i, cy + i, cx + 1/*2*/ + i, cy + i);
			}
			else if (dir == 'W') { // west
				g.<error descr="Cannot resolve method 'drawLine(int, int, int, int)'">drawLine</error>(cx + i, cy + 1 - i, cx + i, cy + 1/*2*/ + i);
			}
			else if (dir == 'S') { // south
				g.<error descr="Cannot resolve method 'drawLine(int, int, int, int)'">drawLine</error>(cx + 1 - i, cy + 4 - i, cx + 1/*2*/ + i, cy + 4 - i);
			}
			else { // east
				g.<error descr="Cannot resolve method 'drawLine(int, int, int, int)'">drawLine</error>(cx + 4 - i, cy + 1 - i, cx + 4 - i, cy + 1/*2*/ + i);
			}
		}
	}

	/**
	 *
	 */
	private void paintContent(Object component, <error descr="Cannot resolve symbol 'Graphics'">Graphics</error> g,
			int clipx, int clipy, int clipwidth, int clipheight,
			int x, int y, int width, int height, <error descr="Cannot resolve symbol 'Color'">Color</error> fg, String defaultalignment,
			boolean checkmnemonic) {
		String text = getString(component, "text", null);
		<error descr="Cannot resolve symbol 'Image'">Image</error> icon = getIcon(component, "icon", null);
		if ((text == null) && (icon == null)) { return; }
		String alignment = getString(component, "alignment", defaultalignment);

		<error descr="Cannot resolve symbol 'FontMetrics'">FontMetrics</error> fm = null; //java
		int tw = 0, th = 0;
		int ta = 0; //java
		if (text != null) {
			fm = g.<error descr="Cannot resolve method 'getFontMetrics()'">getFontMetrics</error>(); //java
			tw = fm.<error descr="Cannot resolve method 'stringWidth(java.lang.String)'">stringWidth</error>(text);
			ta = fm.<error descr="Cannot resolve method 'getAscent()'">getAscent</error>(); //java
			th = fm.<error descr="Cannot resolve method 'getDescent()'">getDescent</error>() + ta; //java
			//midp th = font.getHeight();
			g.<error descr="Cannot resolve method 'setColor(Color)'">setColor</error>(fg);
		}
		int iw = 0, ih = 0;
		if (icon != null) {
			iw = icon.<error descr="Cannot resolve method 'getWidth(Thinlet)'">getWidth</error>(this);
			ih = icon.<error descr="Cannot resolve method 'getHeight(Thinlet)'">getHeight</error>(this); //java
		}

		boolean clipped = (tw + iw > width) || (th > height) || (ih > height);
		int cx = x;
		if ("center" == alignment) { cx += (width - tw - iw) / 2; }
			else if ("right" == alignment) { cx += width - tw - iw; }

		if (clipped) { g.<error descr="Cannot resolve method 'clipRect(int, int, int, int)'">clipRect</error>(x, y, width, height); }
		if (icon != null) {
			g.<error descr="Cannot resolve method 'drawImage(Image, int, int, Thinlet)'">drawImage</error>(icon, cx, y + (height - ih) / 2, this); //java
			//midp g.drawImage(icon, cx, y + height / 2, Graphics.LEFT | Graphics.VCENTER);
			cx += iw;
		}
		if (text != null) {
			int ty = y + (height - th) / 2 + ta; //java
			g.<error descr="Cannot resolve method 'drawString(java.lang.String, int, int)'">drawString</error>(text, cx, ty); //java
			//midp g.drawString(text, cx, y + (height - th) / 2, Graphics.LEFT | Graphics.TOP);
			if (checkmnemonic) {
				int mnemonic = getInteger(component, "mnemonic", -1);
				if ((mnemonic != -1) && (mnemonic < text.length())) {
					int mx = cx + fm.<error descr="Cannot resolve method 'stringWidth(java.lang.String)'">stringWidth</error>(text.substring(0, mnemonic)); //java
					//midp int mx = cx + font.substringWidth(text, 0, mnemonic);
					//midp int ty = (height + th) / 2;
					g.<error descr="Cannot resolve method 'drawLine(int, int, ?, int)'">drawLine</error>(mx, ty + 1, mx + fm.<error descr="Cannot resolve method 'charWidth(char)'">charWidth</error>(text.charAt(mnemonic)), ty + 1);
				}
			}
		}
		if (clipped) { g.<error descr="Cannot resolve method 'setClip(int, int, int, int)'">setClip</error>(clipx, clipy, clipwidth, clipheight); }
	}
	//midp private void setTimer(long delay) {}
	//java>

	/**
	 *
	 */
	public synchronized void run() {
		while (timer == Thread.currentThread()) {
			try {
				if (watch == 0) {
					wait(0);
				} else {
					long current = System.currentTimeMillis();
					if (watch > current) {
						wait(watch - current);
					} else {
						watch = 0;
						if ((watchdelay == 300L) || (watchdelay == 60L)) {
							if (processScroll(mousepressed, pressedpart)) { setTimer(60L); }
						} else if ((watchdelay == 375L) || (watchdelay == 75L)) {
							if (processSpin(mousepressed, pressedpart)) { setTimer(75L); }
						} else if (watchdelay == 750L) {
							//System.out.println("> tip: " + getClass(mouseinside) + " : " + ((insidepart instanceof Object[]) ? getClass(insidepart) : insidepart));
							showTip();
						}
					}
				}
			} catch (InterruptedException ie) {} //ie.printStackTrace();
		}
	}

	/**
	 *
	 */
	private void setTimer(long delay) {
		watchdelay = delay;
		if (delay == 0) {
			watch = 0;
		} else {
			long prev = watch;
			watch = System.currentTimeMillis() + delay;
			if (timer == null) {
				timer = new Thread(this);
				timer.setPriority(Thread.MIN_PRIORITY);
				timer.setDaemon(true);
				timer.start();
			}
			if ((prev == 0) || (watch < prev)) {
				synchronized (this) { notify(); } //try {}catch (IllegalMonitorStateException imse) {}
			}
		}
	}

	/**
	 *
	 */
	public boolean isFocusTraversable() {
		return true;
	}

	//<java
	/*midp
	private static final int MOUSE_ENTERED = 1;
	private static final int MOUSE_EXITED = 2; //
	private static final int MOUSE_PRESSED = 3;
	private static final int MOUSE_DRAGGED = 4;
	private static final int MOUSE_RELEASED = 5;
	private static final int DRAG_ENTERED = 6;
	private static final int DRAG_EXITED = 7;

	protected void pointerPressed(int x, int y) {
		findComponent(content, x, y);
		if (popupowner != null) {
			String classname = getClass(mouseinside);
			if ((popupowner != mouseinside) &&
					(classname != "popupmenu") && (classname != "combolist")) {
				closeup();
			}
		}
		handleMouseEvent(x, y, 1, false, false, false,
			MouseEvent.MOUSE_ENTERED, mouseinside, insidepart);
		mousepressed = mouseinside;
		pressedpart = insidepart;
		handleMouseEvent(x, y, 1, false, false, false,
			MouseEvent.MOUSE_PRESSED, mousepressed, pressedpart);
	}

	protected void pointerReleased(int x, int y) {
		Object mouserelease = mousepressed;
		Object releasepart = pressedpart;
		mousepressed = pressedpart = null;
		handleMouseEvent(x, y, 1, false, false, false,
			MouseEvent.MOUSE_RELEASED, mouserelease, releasepart);
	}

	protected void pointerDragged(int x, int y) {
		Object previnside = mouseinside;
		Object prevpart = insidepart;
		findComponent(content, x, y);
		boolean same = (previnside == mouseinside) && (prevpart == insidepart);
		boolean isin = (mousepressed == mouseinside) && (pressedpart == insidepart);
		boolean wasin = (mousepressed == previnside) && (pressedpart == prevpart);

		if (wasin && !isin) {
			handleMouseEvent(x, y, 1, false, false, false,
				MouseEvent.MOUSE_EXITED, mousepressed, pressedpart);
		}
		else if (!same && (popupowner != null) && !wasin) {
			handleMouseEvent(x, y, 1, false, false, false,
				DRAG_EXITED, previnside, prevpart);
		}
		if (isin && !wasin) {
			handleMouseEvent(x, y, 1, false, false, false,
				MouseEvent.MOUSE_ENTERED, mousepressed, pressedpart);
		}
		else if (!same && (popupowner != null) && !isin) {
			handleMouseEvent(x, y, 1, false, false, false,
				DRAG_ENTERED, mouseinside, insidepart);
		}
		if (isin == wasin) {
			handleMouseEvent(x, y, 1, false, false, false,
				MouseEvent.MOUSE_DRAGGED, mousepressed, pressedpart);
		}
	}

	protected void keyPressed(int keyCode) {
		if ((popupowner != null) || (focusowner != null)) {
			hideTip();
			if (keyCode > 0) {
				processKeyPress((popupowner != null) ? popupowner : focusowner,
					false, false, 1, keyCode, 0);
			}
			else {
				int keychar = 0, key = 0;
				switch (getGameAction(keyCode)) {
					case UP: key = KeyEvent.VK_UP; break;
					case LEFT: key = KeyEvent.VK_LEFT; break;
					case DOWN: key = KeyEvent.VK_DOWN; break;
					case RIGHT: key = KeyEvent.VK_RIGHT; break;
					case FIRE: key = KeyEvent.VK_ENTER; keychar = KeyEvent.VK_SPACE; break;
					case GAME_A: key = KeyEvent.VK_ESCAPE; break;
				}
				if (key != 0) {
					processKeyPress((popupowner != null) ? popupowner : focusowner,
						false, false, 1, keychar, key);
				}
				//if (keyCode == getKeyCode(LEFT)) {
			}
		}
	}

	protected void keyRepeated(int keyCode) {
		keyPressed(keyCode);
	}

	private static final Command nextcommand = new Command("Next", Command.SCREEN, 0);
	//private static final Command prevcommand = new Command("Previous", Command.SCREEN, 0);
	{
		addCommand(nextcommand);
		//addCommand(prevcommand);
		setCommandListener(this);
	}

	public void commandAction(Command command, Displayable displayable) {
		if (command == nextcommand) {
			setNextFocusable(focusowner, false);
			repaint(focusowner);
			closeup();
		}
		//else if (command == prevcommand) {
			//setPreviousFocusable(focusowner, null, true, true, false);
			//repaint(focusowner);
			//closeup();
		//}
	}
	midp*/
	//java>

	/**
	 *
	 */
	protected void processEvent(<error descr="Cannot resolve symbol 'AWTEvent'">AWTEvent</error> e) {
		int id = e.<error descr="Cannot resolve method 'getID()'">getID</error>();
		if ((id >= <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_FIRST) && (id <= <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_LAST)) {
			<error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error> me = (<error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>) e;
			int x = me.<error descr="Cannot resolve method 'getX()'">getX</error>();
			int y = me.<error descr="Cannot resolve method 'getY()'">getY</error>();
			int clickcount = me.<error descr="Cannot resolve method 'getClickCount()'">getClickCount</error>();
			boolean shiftdown = me.<error descr="Cannot resolve method 'isShiftDown()'">isShiftDown</error>();
			boolean controldown = me.<error descr="Cannot resolve method 'isControlDown()'">isControlDown</error>();
			boolean popuptrigger = me.<error descr="Cannot resolve method 'isPopupTrigger()'">isPopupTrigger</error>();
			if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_ENTERED) {
				if (mousepressed == null) {
					findComponent(content, x, y);
					handleMouseEvent(x, y, clickcount, shiftdown, controldown, popuptrigger,
						<error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_ENTERED, mouseinside, insidepart);
				}
			}
			else if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_MOVED) {
				Object previnside = mouseinside;
				Object prevpart = insidepart;
				findComponent(content, x, y);
				if ((previnside == mouseinside) && (prevpart == insidepart)) {
					handleMouseEvent(x, y, clickcount, shiftdown, controldown, popuptrigger,
						<error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_MOVED, mouseinside, insidepart);
				}
				else {
					handleMouseEvent(x, y, clickcount, shiftdown, controldown, popuptrigger,
						<error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_EXITED, previnside, prevpart);
					handleMouseEvent(x, y, clickcount, shiftdown, controldown, popuptrigger,
						<error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_ENTERED, mouseinside, insidepart);
				}
			}
			else if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_EXITED) {
				if (mousepressed == null) {
					Object mouseexit = mouseinside;
					Object exitpart = insidepart;
					mouseinside = insidepart = null;
					handleMouseEvent(x, y, clickcount, shiftdown, controldown, popuptrigger,
						<error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_EXITED, mouseexit, exitpart);
				}
			}
			else if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_PRESSED) {
				if (popupowner != null) {
					String classname = getClass(mouseinside);
					if ((popupowner != mouseinside) &&
							(classname != "popupmenu") && (classname != "combolist")) {
						closeup();
					}
				}
				mousepressed = mouseinside;
				pressedpart = insidepart;
				handleMouseEvent(x, y, clickcount, shiftdown, controldown, popuptrigger,
					<error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_PRESSED, mousepressed, pressedpart);
			}
			else if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_DRAGGED) {
				Object previnside = mouseinside;
				Object prevpart = insidepart;
				findComponent(content, x, y);
				boolean same = (previnside == mouseinside) && (prevpart == insidepart);
				boolean isin = (mousepressed == mouseinside) && (pressedpart == insidepart);
				boolean wasin = (mousepressed == previnside) && (pressedpart == prevpart);

				if (wasin && !isin) {
					handleMouseEvent(x, y, clickcount, shiftdown, controldown, popuptrigger,
						<error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_EXITED, mousepressed, pressedpart);
				}
				else if (!same && (popupowner != null) && !wasin) {
					handleMouseEvent(x, y, clickcount, shiftdown, controldown, popuptrigger,
						DRAG_EXITED, previnside, prevpart);
				}
				if (isin && !wasin) {
					handleMouseEvent(x, y, clickcount, shiftdown, controldown, popuptrigger,
						<error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_ENTERED, mousepressed, pressedpart);
				}
				else if (!same && (popupowner != null) && !isin) {
					handleMouseEvent(x, y, clickcount, shiftdown, controldown, popuptrigger,
						DRAG_ENTERED, mouseinside, insidepart);
				}
				if (isin == wasin) {
					handleMouseEvent(x, y, clickcount, shiftdown, controldown, popuptrigger,
						<error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_DRAGGED, mousepressed, pressedpart);
				}
			}
			else if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_RELEASED) {
				Object mouserelease = mousepressed;
				Object releasepart = pressedpart;
				mousepressed = pressedpart = null;
				handleMouseEvent(x, y, clickcount, shiftdown, controldown, popuptrigger,
					<error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_RELEASED, mouserelease, releasepart);
				if ((mouseinside != null) &&
						((mouserelease != mouseinside) || (releasepart != insidepart))) {
					handleMouseEvent(x, y, clickcount, shiftdown, controldown, popuptrigger,
						<error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_ENTERED, mouseinside, insidepart);
				}
			}
		}
		else if (id == MOUSE_WHEEL) {
			<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> port = getRectangle(mouseinside, ":port");
			if (port != null) { // is scrollable
				<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> bounds = getRectangle(mouseinside, "bounds");
				try { // mouse wheel is supported since 1.4 thus it use reflection
					if (wheelrotation == null) {
						wheelrotation = e.<error descr="Cannot resolve method 'getClass()'">getClass</error>().getMethod("getWheelRotation", null);
					}
					int rotation = ((Integer) wheelrotation.<error descr="Cannot resolve method 'invoke(AWTEvent, null)'">invoke</error>(e, null)).intValue();

					if (port.<error descr="Cannot resolve symbol 'x'">x</error> + port.<error descr="Cannot resolve symbol 'width'">width</error> < bounds.<error descr="Cannot resolve symbol 'width'">width</error>) { // has vertical scrollbar
						processScroll(mouseinside, (rotation > 0) ? "down" : "up");
					}
					else if (port.<error descr="Cannot resolve symbol 'y'">y</error> + port.<error descr="Cannot resolve symbol 'height'">height</error> < bounds.<error descr="Cannot resolve symbol 'height'">height</error>) { // has horizontal scrollbar
						processScroll(mouseinside, (rotation > 0) ? "right" : "left");
					}
				} catch (Exception exc) { /* never */ }
			}
		}
		else if ((id == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.KEY_PRESSED) || (id == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.KEY_TYPED)) {
			if (focusinside && ((popupowner != null) || (focusowner != null))) {
				hideTip();
				<error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error> ke = (<error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>) e;
				int keychar = ke.<error descr="Cannot resolve method 'getKeyChar()'">getKeyChar</error>();
				boolean control = (keychar <= 0x1f) ||
					((keychar >= 0x7f) && (keychar <= 0x9f)) ||
					(keychar >= 0xffff) || ke.<error descr="Cannot resolve method 'isControlDown()'">isControlDown</error>();
				if (control == (id == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.KEY_PRESSED)) {
					int keycode = control ? ke.<error descr="Cannot resolve method 'getKeyCode()'">getKeyCode</error>() : 0;
					if (!processKeyPress((popupowner != null) ? popupowner : focusowner,
							ke.<error descr="Cannot resolve method 'isShiftDown()'">isShiftDown</error>(), ke.<error descr="Cannot resolve method 'isControlDown()'">isControlDown</error>(), ke.<error descr="Cannot resolve method 'getModifiers()'">getModifiers</error>(),
							control ? 0 : keychar, keycode)) {
						if ((keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_TAB) ||
								((keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_F6) && (ke.<error descr="Cannot resolve method 'isAltDown()'">isAltDown</error>() || ke.<error descr="Cannot resolve method 'isControlDown()'">isControlDown</error>()))) {
							boolean outgo = (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_F6);
							if (!ke.<error descr="Cannot resolve method 'isShiftDown()'">isShiftDown</error>() ? setNextFocusable(focusowner, outgo) :
									setPreviousFocusable(focusowner, outgo)) {
								ke.<error descr="Cannot resolve method 'consume()'">consume</error>();
							} else if (MOUSE_WHEEL != 0) { // 1.4
								if (!ke.<error descr="Cannot resolve method 'isShiftDown()'">isShiftDown</error>()) {
									transferFocus();
								}
								else { try {
										getClass().getMethod("transferFocusBackward", null). <error descr="Cannot resolve method 'invoke(Thinlet, null)'">invoke</error>(this, null);
								} catch (Exception exc) { /* never */ } }
							}
							repaint(focusowner);
							closeup();
						}
						else if (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_F8) {
							for (Object splitpane = focusowner;
									splitpane != null; splitpane = getParent(splitpane)) {
								if (getClass(splitpane) == "splitpane") {
									setFocus(splitpane); repaint(splitpane); break; //middle
								}
							}
						}
					}
					else ke.<error descr="Cannot resolve method 'consume()'">consume</error>();
					/*else if (keycode == KeyEvent.VK_F10) {
						Object menubar = null; // find("class", "menubar")
						if ((menubar != null) && (get(menubar, "selected") == null)) {
							set(menubar, "selected", getMenu(menubar, null, true, false));
							Object popup = popup(menubar, "menubar");
							set(popup, "selected", getMenu(popup, null, true, true));
							repaint(menubar); // , selected
						}
					}*/
				}
			}
		}
		/*else if (id == KeyEvent.KEY_RELEASED) {
			if (focusinside && (focusowner != null)) {
				KeyEvent ke = (KeyEvent) e;
				//pressedkey = 0;
				processKeyRelease(focusowner, ke, ke.getKeyCode());
			}
		}*/
		else if (id == <error descr="Cannot resolve symbol 'FocusEvent'">FocusEvent</error>.FOCUS_LOST) {
			focusinside = false;
			if (focusowner != null) { repaint(focusowner); }
			closeup();
		}
		else if (id == <error descr="Cannot resolve symbol 'FocusEvent'">FocusEvent</error>.FOCUS_GAINED) {
			focusinside = true;
			if (focusowner == null) { setFocus(content); }
				else { repaint(focusowner); }
		}
		else if ((id == <error descr="Cannot resolve symbol 'ComponentEvent'">ComponentEvent</error>.COMPONENT_RESIZED) ||
				(id == <error descr="Cannot resolve symbol 'ComponentEvent'">ComponentEvent</error>.COMPONENT_SHOWN)) {
			Dimension d = getSize();
			//System.out.println(id + ": " + d.width + ", " + d.height);
			setRectangle(content, "bounds", 0, 0, d.width, d.height);
			validate(content);
			closeup();
			if (!focusinside)  { requestFocus(); }
		}
	}

	/**
	 *
	 */
	/*private boolean processKeyPress(Object component,
			KeyEvent e, int keycode, Object invoker) {
		if (processKeyPress(component, e, keycode)) { return true; }
		for (Object comp = get(component, "component");
				comp != null; comp = get(comp, ":next")) {
			if ((comp != invoker) && processKeyPress(comp, e, keycode, null)) {
				return true;
			}
		}
		if ((invoker != null) && (component != content)) {
			Object parent = getParent(component);
			if (parent != null) {
				return processKeyPress(parent, e, keycode, component);
			}
		}
		return false;
	}*/
	//<java

	/**
	 *
	 */
	private boolean processKeyPress(Object component,
			boolean shiftdown, boolean controldown, int modifiers, int keychar, int keycode) {
		String classname = getClass(component);
		if ("button" == classname) {
			if (keychar == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_SPACE ||
					((keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_ENTER) &&
						(getString(component, "type", null) == "default")) ||
					((keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_ESCAPE) && //...
						(getString(component, "type", null) == "cancel"))) {
				//pressedkey = keychar;
				invoke(component, "action");
				repaint(component);
				return true;
			}
		}
		else if ("checkbox" == classname) {
			if (keychar == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_SPACE) {
				changeCheck(component, true);
				repaint(component);
				return true;
			}
		}
		else if ("combobox" == classname) {
			Object combolist = get(component, "combolist");
			if (combolist == null) {
				if (getBoolean(component, "editable", true) &&
						processField(component, shiftdown, controldown, modifiers,
							keychar, keycode, false, false)) {
					setInteger(component, "selected", -1, -1);
					return true;
				}
				if ((keychar == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_SPACE) || (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_DOWN)) {
					combolist = popup(component, classname);
					int selected = getInteger(component, "selected", -1);
					set(combolist, "inside", (selected != -1) ?
						getItemImpl(component, "choice", selected) :
						get(component, "choice")); //scroll to it!
				}
				else return false;
			} else {
				if ((keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_UP) ||
						(keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_DOWN) || (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_PAGE_UP) ||
						(keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_PAGE_DOWN) ||
						(keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_HOME) || (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_END)) {
					Object selected = get(combolist, "inside");
					Object next = getListItem(component, combolist,
						keycode, selected, "choice", null);
					if (next != null) {
						set(combolist, "inside", next);
						<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> r = getRectangle(next, "bounds");
						scrollToVisible(combolist, r.<error descr="Cannot resolve symbol 'x'">x</error>, r.<error descr="Cannot resolve symbol 'y'">y</error>, 0, r.<error descr="Cannot resolve symbol 'height'">height</error>);
						if (selected != null) { repaint(combolist, "combolist", selected); }
						repaint(combolist, "combolist", next);
					}
				}
				else if ((keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_ENTER) || (keychar == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_SPACE)) {
					closeup(component, combolist, get(combolist, "inside")); //Alt+Up
				}
				else if (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_ESCAPE) {
					closeup(component, combolist, null);
				}
				else return processField(component, shiftdown, controldown, modifiers,
					keychar, keycode, false, false);
			}
			return true;
		}
		else if (("textfield" == classname) || ("passwordfield" == classname)) {
			return processField(component, shiftdown, controldown, modifiers,
				keychar, keycode, false, ("passwordfield" == classname));
		}
		else if ("textarea" == classname) {
			String text = getString(component, "text", "");
			int start = getInteger(component, "start", 0);
			int end = getInteger(component, "end", 0);

			int istart = start;
			int iend = end;
			String insert = null;
			if ((keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_HOME) && !controldown) {
				while ((iend > 0) && (text.charAt(iend - 1) != '\n')) { iend--; }
				if (!shiftdown) { istart = iend; }
			}
			else if ((keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_END) && !controldown) {
				iend = text.indexOf('\n', end);
				if (iend == -1) { iend = text.length(); }
				if (!shiftdown) { istart = iend; }
			}
			else if ((keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_UP) ||
					(keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_PAGE_UP)) {
				int prev = end;
				while ((prev > 0) && (text.charAt(prev - 1) != '\n')) { prev--; }
				if (prev != 0) {
					int dx = end - prev;
					int lines = (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_PAGE_UP) ?
						(getRectangle(component, ":port").<error descr="Cannot resolve symbol 'height'">height</error> /
							getFontMetrics(getFont()).<error descr="Cannot resolve method 'getHeight()'">getHeight</error>()) : 1;
					int first = prev;
					do {
						prev = first; first--; lines--;
						while ((first > 0) && (text.charAt(first - 1) != '\n')) { first--; }
					} while ((first > 0) && (lines > 0));
					iend = Math.min(first + dx, prev - 1);
					if (!shiftdown) { istart = iend; }
				}
			}
			else if ((keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_DOWN) ||
					(keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_PAGE_DOWN)) {
				int next = text.indexOf('\n', end);
				if (next != -1) {
					int prev = end;
					while ((prev > 0) && (text.charAt(prev - 1) != '\n')) { prev--; }
					if (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_PAGE_DOWN) {
						int lines = getRectangle(component, ":port").<error descr="Cannot resolve symbol 'height'">height</error> /
							getFontMetrics(getFont()).<error descr="Cannot resolve method 'getHeight()'">getHeight</error>();
						for (int more = 0; (lines > 1) &&
								((more = text.indexOf('\n', next + 1)) != -1); next = more) {
							lines--;
						}
					}
					int last = text.indexOf('\n', next + 1);
					iend = Math.min(next + 1 + end - prev,
						(last == -1) ? (text.length() + 1) : last);
					if (!shiftdown) { istart = iend; }
				}
			}
			return changeField(component, text, insert, istart, iend, start, end) ?
				true : processField(component, shiftdown, controldown, modifiers,
					keychar, keycode, true, false);
		}
		else if ("tabbedpane" == classname) {
			if ((keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_RIGHT) || (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_DOWN) ||
					(keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_LEFT) || (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_UP)) {
				int selected = getInteger(component, "selected", 0);
				boolean increase = (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_RIGHT) || (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_DOWN);
				int newvalue = selected;
				int n = increase ? getItemCountImpl(component, "tab") : 0;
				int d = (increase ? 1 : -1);
				for (int i = selected + d; increase ? (i < n)  : (i >= 0); i += d) {
					if (getBoolean(getItemImpl(component, "tab", i), "enabled", true)) {
						newvalue = i; break;
					}
				}
				if (newvalue != selected) {
					setInteger(component, "selected", newvalue, 0);
					repaint(component);
					invoke(component, "action");
				}
			}
		}
		else if ("spinbox" == classname) {
			if ((keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_UP) || (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_DOWN)) {
				processSpin(component, (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_UP)? "up" : "down");
				return true;
			}
			return processField(component, shiftdown, controldown, modifiers,
				keychar, keycode, false, false);
		}
		else if ("slider" == classname) {
			int value = getInteger(component, "value", 0);
			int d = 0;
			if ((keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_HOME) || (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_LEFT) ||
					(keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_UP) || (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_PAGE_UP)) {
				d = getInteger(component, "minimum", 0) - value;
				if ((keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_LEFT) || (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_UP)) {
					d = Math.max(d, -getInteger(component, "unit", 5));
				}
				else if (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_PAGE_UP) {
					d = Math.max(d, -getInteger(component, "block", 25));
				}
			}
			else if ((keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_END) || (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_RIGHT) ||
					(keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_DOWN) || (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_PAGE_DOWN)) {
				d = getInteger(component, "maximum", 100) - value;
				if ((keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_RIGHT) || (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_DOWN)) {
					d = Math.min(d, getInteger(component, "unit", 5));
				}
				else if (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_PAGE_DOWN) {
					d = Math.min(d, getInteger(component, "block", 25));
				}
			}
			if (d != 0) {
				setInteger(component, "value", value + d, 0);
				repaint(component);
				invoke(component, "action");
			}
		}
		else if ("splitpane" == classname) {
			int divider = getInteger(component, "divider", -1);
			int d = 0;
			if (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_HOME) {
				d = -divider;
			}
			else if ((keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_LEFT) || (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_UP)) {
				d = Math.max(-10, -divider);
			}
			else if ((keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_END) ||
					(keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_RIGHT) || (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_DOWN)) {
				boolean horizontal = ("vertical" != get(component, "orientation"));
				<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> bounds = getRectangle(component, "bounds");
				int max = (horizontal ? bounds.<error descr="Cannot resolve symbol 'width'">width</error> : bounds.<error descr="Cannot resolve symbol 'height'">height</error>) - 5;
				d = max - divider;
				if (keycode != <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_END) {
					d = Math.min(d, 10);
				}
			}
			if (d != 0) {
				setInteger(component, "divider", divider + d, -1);
				validate(component);
			}
		}
		else if ("list" == classname) {
			return processList(component, shiftdown, controldown, keychar, keycode, "item", null);
		}
		else if ("table" == classname) {
			return processList(component, shiftdown, controldown, keychar, keycode, "row", null);
		}
		else if ("tree" == classname) {
			//? clear childs' selection, select this is its 	subnode was selected
			if (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_LEFT) {
				Object lead = get(component, "lead");
				if ((get(lead, "node") != null) && getBoolean(lead, "expanded", true)) {
					setBoolean(lead, "expanded", false, true);
					selectItem(component, lead, "node", "node");
					validate(component);
					invoke(component, "collapse"); //lead
					return true;
				}
				else {
					Object parent = getParent(lead);
					if (parent != component) {
						selectItem(component, parent, "node", "node");
						setLead(component, lead, parent);
						return true;
					}
				}
			}
			//? for interval mode select its all subnode or deselect all after
			else if (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_RIGHT) {
				Object lead = get(component, "lead");
				Object node = get(lead, "node");
				if (node != null) {
					if (getBoolean(lead, "expanded", true)) {
						selectItem(component, node, "node", "node");
						setLead(component, lead, node);
					}
					else {
						setBoolean(lead, "expanded", true, true);
						selectItem(component, lead, "node", "node");
						validate(component);
						invoke(component, "expand"); //lead
					}
					return true;
				}
			}
			return processList(component, shiftdown, controldown, keychar, keycode, "node", "node");
		}
		else if ("menubar" == classname) {
			Object previous = null; Object last = null;
			for (Object i = get(component, "popupmenu");
					i != null; i = get(i, "popupmenu")) {
				previous = last; last = i;
			}
			Object selected = get(last, "selected");
			Object hotpopup = ((selected != null) || (previous == null)) ?
				last : previous;
			if ((selected == null) && (previous != null)) {
				selected = get(previous, "selected");
			}

			if ((keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_UP) || (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_DOWN)) {
				set(hotpopup, "selected", null);
				popup(hotpopup, "popupmenu");
				selected = getMenu(hotpopup,
					selected, keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_DOWN, true);
				set(hotpopup, "selected", selected);
				repaint(hotpopup);
			}
			else if (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_LEFT) {
				if (previous != null) {
					selected = get(previous, "selected");
					set(previous, "selected", null);
					popup(previous, "popupmenu");
					set(previous, "selected", selected);
					repaint(previous); // , selected
				}
				else {
					selected = getMenu(component, get(component, "selected"), false, false);
					set(component, "selected", selected);
					Object popup = popup(component, "menubar");
					set(popup, "selected", getMenu(popup, null, true, true));
					repaint(component); // , selected
				}
			}
			else if (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_RIGHT) {
				if ((previous != null) && (selected == null)) {
					set(last, "selected", get(get(last, "menu"), "menu"));
					repaint(last); // , selected
				}
				else if ((selected != null) && (getClass(selected) == "menu")) {
					Object popup = popup(last, "popupmenu");
					set(popup, "selected", get(get(popup, "menu"), "menu"));
				}
				else {
					selected = getMenu(component, get(component, "selected"), true, false);
					set(component, "selected", selected);
					Object popup = popup(component, "menubar");
					set(popup, "selected", getMenu(popup, null, true, true));
					repaint(component); // , selected
				}
			}
			else if ((keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_ENTER) ||
					(keychar == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_SPACE) || (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_ESCAPE)) {
				if ((keycode != <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_ESCAPE) &&
						getBoolean(selected, "enabled", true)) {
					if ((selected != null) && (getClass(selected) == "checkboxmenuitem")) {
						changeCheck(selected, false);
					}
					else invoke(selected, "action");
				}
				closeup(component);
			}
			else return false;
			return true;
		}
		return false;
	}

	/**
	 *
	 */
	private boolean changeCheck(Object component, boolean box) {
		String group = getString(component, "group", null);
		if (group != null) {
			if (getBoolean(component, "selected", false)) { return false; }
			for (Object comp = get(getParent(component),
					box ? "component" : "menu"); comp != null; comp = get(comp, ":next")) {
				if (comp == component) {
					setBoolean(component, "selected", true);
				}
				else if (group.equals(get(comp, "group")) &&
						getBoolean(comp, "selected", false)) {
					setBoolean(comp, "selected", false);
					if (box) { repaint(comp); } //checkbox only
				}
			}
		}
		else {
			setBoolean(component, "selected",
				!getBoolean(component, "selected", false), false);
		}
		invoke(component, "action");
		return true;
	}

	/**
	 *
	 */
	private Object getMenu(Object component, Object part,
			boolean forward, boolean popup) {
		if (forward) {
			if (part != null) { part = get(part, ":next"); }
			if (part == null) {
				part = get(popup ? get(component, "menu") : component, "menu");
			}
		}
		else {
			Object prev = get(popup ? get(component, "menu") : component, "menu");
			for (Object next = get(prev, ":next");
					(next != null) && (next != part); next = get(next, ":next")) {
				prev = next;
			}
			part = prev;
		}
		return (getClass(part) == "separator") ?
			getMenu(component, part, forward, popup) : part;
	}

	/**
	 *
	 */
	/*private boolean processKeyRelease(Object component, KeyEvent e, int keycode) {
		return true;
	}*/

	/**
	 *
	 */
	private boolean processField(Object component,
			boolean shiftdown, boolean controldown, int modifiers,
			int keychar, int keycode, boolean multiline, boolean hidden) {
		String text = getString(component, "text", "");
		int start = getInteger(component, "start", 0);
		int end = getInteger(component, "end", 0);
		boolean editable = getBoolean(component, "editable", true);

		int istart = start;
		int iend = end;
		String insert = null;
		//midp if (editable && (keychar != 0)) {
		if (editable && (keychar != 0) && //java
			((modifiers == 0) || (modifiers == <error descr="Cannot resolve symbol 'InputEvent'">InputEvent</error>.SHIFT_MASK))) { //java
			insert = String.valueOf((char) keychar);
		}
		else if (multiline && editable && (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_ENTER)) {
			insert = "\n";
		}
		else if (editable && (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_BACK_SPACE)) {
			insert = "";
			if (start == end) { istart -= 1; }
		}
		else if (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_END) {
			iend = text.length();
			if (!shiftdown) { istart = iend; }
		}
		else if (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_HOME) {
			iend = 0;
			if (!shiftdown) { istart = iend; }
		}
		else if (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_LEFT) {
			//java>
			if (controldown) {
				for (int i = 0; i < 2; i++) {
					while ((iend > 0) && ((i != 0) ==
						Character.isLetterOrDigit(text.charAt(iend - 1)))) { iend--; }
				}
			} else {
				iend -= 1;
			}
			//<java
			//midp iend -= 1;
			if (!shiftdown) { istart = iend; }
		}
		else if (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_RIGHT) {
			//java>
			if (controldown) {
				for (int i = 0; i < 2; i++) {
					while ((iend < text.length()) && ((i == 0) ==
						Character.isLetterOrDigit(text.charAt(iend)))) { iend++; }
				}
			} else {
				iend += 1;
			}
			//<java
			//midp iend += 1;
			if (!shiftdown) { istart = iend; }
		}
		else if (editable && (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_DELETE)) {
			insert = "";
			if (start == end) { iend += 1; }
		}
		else if (controldown &&
				((keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_A) || (keycode == 0xBF))) {
			istart = 0; // KeyEvent.VK_SLASH
			iend = text.length();
		}
		else if (controldown && (keycode == 0xDC)) {
			istart = iend = text.length(); // KeyEvent.VK_BACK_SLASH
		}
		else if ((editable && !hidden && controldown && (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_X)) ||
				(!hidden && controldown && (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_C))) {
			if (start != end) {
				clipboard = text.substring(
					Math.min(start, end), Math.max(start, end));
				//java>
				try {
					/* Personal Basis Profile doesn't contain datatransfer package
					Toolkit toolkit = getToolkit();
					Object systemclipboard = toolkit.getClass().getMethod("getSystemClipboard", null).invoke(toolkit, null);
					Class selectionclass = Class.forName("java.awt.datatransfer." + "StringSelection");
					Object selection = selectionclass.getConstructor(new Class[] { String.class }).
						newInstance(new Object[] { clipboard });
					systemclipboard.getClass().getMethod("setContents", new Class[] {
							Class.forName("java.awt.datatransfer." + "Transferable"),
							Class.forName("java.awt.datatransfer." + "ClipboardOwner") }).
						invoke(systemclipboard, new Object[] { selection, null });*/
					getToolkit().<error descr="Cannot resolve method 'getSystemClipboard()'">getSystemClipboard</error>().setContents(
						new <error descr="Cannot resolve symbol 'StringSelection'">StringSelection</error>(clipboard), null);
				} catch (Exception exc) {}
				//<java
				if (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_X) { insert = ""; }
			}
		}
		else if (editable && controldown && (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_V)) {
			//java>
			try {
				/* no datatransfer package in PBP
				Toolkit toolkit = getToolkit();
				Object systemclipboard = toolkit.getClass().getMethod("getSystemClipboard", null).invoke(toolkit, null);
				Object contents = systemclipboard.getClass().getMethod("getContents", new Class[] { Object.class }).
					invoke(systemclipboard, new Object[] { this });
				Class dataflavor = Class.forName("java.awt.datatransfer." + "DataFlavor");
				insert = (String) (contents.getClass().getMethod("getTransferData", new Class[] { dataflavor }).
					invoke(contents, new Object[] { dataflavor.getField("stringFlavor").get(null) }));*/
				insert = (String) getToolkit().<error descr="Cannot resolve method 'getSystemClipboard()'">getSystemClipboard</error>().
					getContents(this).getTransferData(<error descr="Cannot resolve symbol 'DataFlavor'">DataFlavor</error>.stringFlavor);
			} catch (Exception exc) {
				insert = clipboard;
			}
			//<java
			//midp insert = clipboard;
			StringBuffer filtered = new StringBuffer(insert.length());
			for (int i = 0; i < insert.length(); i++) {
				char ckey = insert.charAt(i);
				if (((ckey > 0x1f) && (ckey < 0x7f)) ||
						((ckey > 0x9f) && (ckey < 0xffff)) ||
						(multiline && (ckey == '\n'))) {
					filtered.append(ckey);
				}
			}
			if (filtered.length() != insert.length()) {
				insert = filtered.toString();
			}
		}
		return changeField(component, text, insert, istart, iend, start, end);
	}

	/**
	 *
	 */
	private boolean changeField(Object component, String text, String insert,
			int movestart, int moveend, int start, int end) {
		if ((insert == null) && (start == movestart) && (end == moveend)) {
			return false;
		}
		movestart = Math.max(0, Math.min(movestart, text.length()));
		moveend = Math.max(0, Math.min(moveend, text.length()));
		if (insert != null) {
			int min = Math.min(movestart, moveend);
			set(component, "text", text.substring(0, min) + insert +
				text.substring(Math.max(movestart, moveend)));
			movestart = moveend = min + insert.length();
			invoke(component, "action");
		}
		if (start != movestart) { setInteger(component, "start", movestart, 0); }
		if (end != moveend) { setInteger(component, "end", moveend, 0); }
		if ((insert != null) || (start != movestart) || (end != moveend)) {
			validate(component);
		}
		return true;
	}

	/**
	 *
	 */
	private boolean processList(Object component, boolean shiftdown, boolean controldown,
			int keychar, int keycode, String itemname, String leafname) {
		if ((keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_UP) ||
				(keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_DOWN) || (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_PAGE_UP) ||
				(keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_PAGE_DOWN) ||
				(keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_HOME) || (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_END)) {
			Object lead = get(component, "lead");
			Object row = getListItem(component, component,
				keycode, lead, itemname, leafname);
			if (row != null) {
				String selection = getString(component, "selection", "single");
				if (shiftdown && (selection != "single") && (lead != null)) {
					extend(component, lead, row, itemname, leafname);
				}
				else if (!controldown) {
					selectItem(component, row, itemname, leafname);
				}
				setLead(component, lead, row);
				<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> r = getRectangle(row, "bounds");
				scrollToVisible(component, r.<error descr="Cannot resolve symbol 'x'">x</error>, r.<error descr="Cannot resolve symbol 'y'">y</error>, 0, r.<error descr="Cannot resolve symbol 'height'">height</error> - 1);
				return true;
			}
		}
		else if (keychar == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_SPACE) {
			select(component, get(component, "lead"),
				itemname, leafname, shiftdown, controldown); //...
			return true;
		}
		else if (controldown) {
			if (((keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_A) || (keycode == 0xBF)) && //KeyEvent.VK_SLASH
					(getString(component, "selection", "single") != "single")) {
				selectAll(component, true, itemname, leafname);
				return true;
			}
			else if (keycode == 0xDC) { //KeyEvent.VK_BACK_SLASH
				selectAll(component, false, itemname, leafname);
				return true;
			}
		}
		return false;
	}

	/**
	 *
	 */
	private Object getListItem(Object component, Object scrollpane,
			int keycode, Object lead, String itemname, String leafname) {
		Object row = null;
		if (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_UP) {
			for (Object prev = get(component, itemname); prev != lead;
					prev = getNextItem(component, prev, leafname)) {
				row = prev; // component -> getParent(lead)
			}
		}
		else if (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_DOWN) {
			row = (lead == null) ? get(component, itemname) :
				getNextItem(component, lead, leafname);
		}
		else if ((keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_PAGE_UP) ||
				(keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_PAGE_DOWN)) {
			<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> view = getRectangle(scrollpane, ":view");
			<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> port = getRectangle(scrollpane, ":port");
			<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> rl = (lead != null) ? getRectangle(lead, "bounds") : null;
			int vy = (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_PAGE_UP) ?
				view.<error descr="Cannot resolve symbol 'y'">y</error> : (view.<error descr="Cannot resolve symbol 'y'">y</error> + port.<error descr="Cannot resolve symbol 'height'">height</error> - 2);
			if ((keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_PAGE_UP) &&
					(rl != null) && (rl.<error descr="Cannot resolve symbol 'y'">y</error> <= view.<error descr="Cannot resolve symbol 'y'">y</error>)) {
				vy -= port.<error descr="Cannot resolve symbol 'height'">height</error> - 2;
			}
			if ((keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_PAGE_DOWN) &&
					(rl != null) && (rl.<error descr="Cannot resolve symbol 'y'">y</error> + rl.<error descr="Cannot resolve symbol 'height'">height</error> >= view.<error descr="Cannot resolve symbol 'y'">y</error> + port.<error descr="Cannot resolve symbol 'height'">height</error> - 2)) {
				vy += port.<error descr="Cannot resolve symbol 'height'">height</error> - 2;
			}
			for (Object item = get(component, itemname); item != null;
					item = getNextItem(component, item, leafname)) {
				<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> r = getRectangle(item, "bounds");
				if (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_PAGE_UP) {
					row = item;
					if (r.<error descr="Cannot resolve symbol 'y'">y</error> + r.<error descr="Cannot resolve symbol 'height'">height</error> > vy) { break; }
				} else {
					if (r.<error descr="Cannot resolve symbol 'y'">y</error> > vy) { break; }
					row = item;
				}
			}
		}
		else if (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_HOME) {
			row = get(component, itemname);
		}
		else if (keycode == <error descr="Cannot resolve symbol 'KeyEvent'">KeyEvent</error>.VK_END) {
			for (Object last = lead; last != null;
					last = getNextItem(component, last, leafname)) {
				row = last;
			}
		}
		return row;
	}

	/**
	 *
	 */
	private void selectAll(Object component,
			boolean selected, String itemname, String leafname) {
		boolean changed = false;
		for (Object item = get(component, itemname);
				item != null; item = getNextItem(component, item, leafname)) {
			if (setBoolean(item, "selected", selected, false)) {
				repaint(component, null, item); changed = true;
			}
		}
		set(component, "anchor", null);
		if (changed) {invoke(component, "action"); }
	}

	/**
	 *
	 */
	private void selectItem(Object component,
			Object row, String itemname, String leafname) {
		boolean changed = false;
		for (Object item = get(component, itemname);
				item != null; item = getNextItem(component, item, leafname)) {
			if (setBoolean(item, "selected", (item == row), false)) {
				repaint(component, null, item); changed = true;
			}
		}
		set(component, "anchor", null);
		if (changed) { invoke(component, "action"); }
	}

	/**
	 *
	 */
	private void extend(Object component, Object lead,
			Object row, String itemname, String leafname) {
		Object anchor = get(component, "anchor");
		if (anchor == null) { set(component, "anchor", anchor = lead); }
		char select = 'n'; boolean changed = false;
		for (Object item = get(component, itemname); // anchor - row
				item != null; item = getNextItem(component, item, leafname)) {
			if (item == anchor) select = (select == 'n') ? 'y' : 'r';
			if (item == row) select = (select == 'n') ? 'y' : 'r';
			if (setBoolean(item, "selected", (select != 'n'), false)) {
				repaint(component, null, item); changed = true;
			}
			if (select == 'r') select = 'n';
		}
		if (changed) { invoke(component, "action"); }
	}

	/**
	 *
	 */
	private void setLead(Object component, Object oldlead, Object lead) {
		if (oldlead != lead) { //?
			if (oldlead != null) { repaint(component, null, oldlead); }
			set(component, "lead", lead);
			repaint(component, null, lead);
		}
	}

	/*public void repaint(int x, int y, int width, int height) {
		System.out.println("repaint(" + x + ", " + y + ", " + width + ", " + height + ")");
		super.repaint(x, y, width, height);
	}*/

	/**
	 *
	 */
	private void handleMouseEvent(int x, int y, int clickcount,
			boolean shiftdown, boolean controldown, boolean popuptrigger,
			int id, Object component, Object part) {
		if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_ENTERED) {
			setTimer(750L);
		}
		else if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_EXITED) {
			hideTip();
		}
		if (!getBoolean(component, "enabled", true)) { return; }
		String classname = getClass(component);
		if (("button" == classname) || ("checkbox" == classname)) {
			if ((id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_ENTERED) ||
					(id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_EXITED) ||
					(id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_PRESSED) ||
					(id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_RELEASED)) {
				if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_PRESSED) {
					setFocus(component);
				}
				else if ((id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_RELEASED) &&
						(mouseinside == component)) {
					if ("checkbox" == classname) {
						changeCheck(component, true);
					}
					else invoke(component, "action");
				}
				repaint(component);
			}
		}
		else if ("combobox" == classname) {
			boolean editable = getBoolean(component, "editable", true);
			if (editable && (part == null)) {
				<error descr="Cannot resolve symbol 'Image'">Image</error> icon = null;
				int left = ((id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_PRESSED) &&
					((icon = getIcon(component, "icon", null)) != null)) ?
						icon.<error descr="Cannot resolve method 'getWidth(Thinlet)'">getWidth</error>(this) : 0;
				processField(x, y, clickcount, id, component, part, false, false, left);
			}
			else if (part != "icon") { // part = "down"
				if (((id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_ENTERED) ||
						(id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_EXITED)) && (mousepressed == null)) {
					if (editable) { repaint(component, "combobox", part); }
						else { repaint(component); }
				}
				else if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_PRESSED) {
					Object combolist = get(component, "combolist");
					if (combolist == null) {
						setFocus(component);
						repaint(component);
						popup(component, classname);
					} else {
						closeup(component, combolist, null);
					}
				}
				else if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_RELEASED) {
					if (mouseinside != component) {
						Object combolist = get(component, "combolist");
						closeup(component, combolist,
							(mouseinside == combolist) ? insidepart : null);
					} else {
						repaint(component);
					}
				}
			}
		}
		else if ("combolist" == classname) {
			if (!processScroll(x, y, id, component, part)) {
				if ((id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_ENTERED) || (id == DRAG_ENTERED)) {
					if (part != null) {
						// repaint previous inside
						set(component, "inside", part);
						repaint(component, classname, part);
					}
				}
				else if ((id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_EXITED) || (id == DRAG_EXITED)) {
					if (part != null) {
						set(component, "inside", null);
						repaint(component, classname, part);
					}
				}
				else if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_RELEASED) {
					closeup(get(component, "combobox"), component, part);
				}
			}
		}
		else if (("textfield" == classname) || ("passwordfield" == classname)) {
			processField(x, y, clickcount, id, component, part,
				false, ("passwordfield" == classname), 0);
		}
		else if ("textarea" == classname) {
			if (!processScroll(x, y, id, component, part)) {
				processField(x, y, clickcount, id, component, part, true, false, 0);
			}
		}
		//java>
		else if ("desktop" == classname) {
			if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_ENTERED) {
				setCursor(<error descr="Cannot resolve symbol 'Cursor'">Cursor</error>.getPredefinedCursor(<error descr="Cannot resolve symbol 'Cursor'">Cursor</error>.WAIT_CURSOR));
			}
			else if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_EXITED) {
				setCursor(<error descr="Cannot resolve symbol 'Cursor'">Cursor</error>.getPredefinedCursor(<error descr="Cannot resolve symbol 'Cursor'">Cursor</error>.DEFAULT_CURSOR));
			}
		}
		//<java
		else if ("spinbox" == classname) {
			if (part == null) {
				processField(x, y, clickcount, id, component, part, false, false, 0);
			}
			else { // part = "up" || "down"
				if ((id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_ENTERED) ||
						(id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_EXITED) ||
						(id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_PRESSED) ||
						(id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_RELEASED)) {
					if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_PRESSED) {
						setFocus(component);
						if (processSpin(component, part)) { setTimer(375L); }
						//settext: start end selection, parse exception...
					}
					else {
						if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_RELEASED) {
							setTimer(0L);
						}
					}
					repaint(component, classname, part);
				}
			}
		}
		else if ("tabbedpane" == classname) {
			if ((id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_ENTERED) ||
					(id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_EXITED)) {
				if ((part != null) && getBoolean(part, "enabled", true) &&
						(getInteger(component, "selected", 0) != getIndex(component, "tab", part))) {
					repaint(component, "tabbedpane", part);
				}
			}
			else if ((part != null) && (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_PRESSED) &&
					getBoolean(part, "enabled", true)) {
				int selected = getInteger(component, "selected", 0);
				int current = getIndex(component, "tab", part);
				if (selected == current) {
					setFocus(component);
					repaint(component, "tabbedpane", part);
				}
				else {
					setInteger(component, "selected", current, 0);
					//Object tabcontent = getItemImpl(component, "component", current);
					//setFocus((tabcontent != null) ? tabcontent : component);
					setNextFocusable(component, false);
					repaint(component);
					invoke(component, "action");
				}
			}
		}
		else if ("slider" == classname) {
			if ((id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_PRESSED) ||
					(id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_DRAGGED)) {
				if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_PRESSED) {
					setReference(component, block / 2, block / 2);
					setFocus(component);
				}
				int minimum = getInteger(component, "minimum", 0);
				int maximum = getInteger(component, "maximum", 100);
				int value = getInteger(component, "value", 50);
				<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> bounds = getRectangle(component, "bounds");
				boolean horizontal = ("vertical" != get(component, "orientation"));
				int newvalue = minimum +
					(horizontal ? (mousex - referencex) : (mousey - referencey)) *
					(maximum - minimum) /
					((horizontal ? bounds.<error descr="Cannot resolve symbol 'width'">width</error> : bounds.<error descr="Cannot resolve symbol 'height'">height</error>) - block); //... +0.5
				newvalue = Math.max(minimum, Math.min(newvalue, maximum));
				if (value != newvalue) {
					setInteger(component, "value", newvalue, 50);
					invoke(component, "action");
				}
				if ((value != newvalue) || (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_PRESSED)) {
					repaint(component);
				}
			}
		}
		else if ("splitpane" == classname) {
			if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_PRESSED) {
				setReference(component, 2, 2);
			}
			else if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_DRAGGED) {
				int divider = getInteger(component, "divider", -1);
				boolean horizontal = ("vertical" != get(component, "orientation"));
				int moveto = horizontal ? (mousex - referencex) :
					(mousey - referencey);
				<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> bounds = getRectangle(component, "bounds");
				moveto = Math.max(0, Math.min(moveto,
					Math.abs(horizontal ? bounds.<error descr="Cannot resolve symbol 'width'">width</error> : bounds.<error descr="Cannot resolve symbol 'height'">height</error>) - 5));
				if (divider != moveto) {
					setInteger(component, "divider", moveto, -1);
					validate(component);
				}
			}
			//java>
			else if ((id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_ENTERED) && (mousepressed == null)) {
				boolean horizontal = ("vertical" != get(component, "orientation"));
				setCursor(<error descr="Cannot resolve symbol 'Cursor'">Cursor</error>.getPredefinedCursor(horizontal ?
					<error descr="Cannot resolve symbol 'Cursor'">Cursor</error>.E_RESIZE_CURSOR : <error descr="Cannot resolve symbol 'Cursor'">Cursor</error>.S_RESIZE_CURSOR));
			}
			else if (((id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_EXITED) && (mousepressed == null)) ||
					((id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_RELEASED) && (mouseinside != component))) {
				setCursor(<error descr="Cannot resolve symbol 'Cursor'">Cursor</error>.getPredefinedCursor(<error descr="Cannot resolve symbol 'Cursor'">Cursor</error>.DEFAULT_CURSOR));
			}
			//<java
		}
		else if (("list" == classname) ||
				("table" == classname) || ("tree" == classname)) {
			if (!processScroll(x, y, id, component, part)) {
				if (((id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_PRESSED)||
						((id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_DRAGGED) &&
							!shiftdown && !controldown)) &&
						!popuptrigger) { // e.getClickCount()
					<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> view = getRectangle(component, ":view");
					<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> viewport = getRectangle(component, ":port");
					int my = mousey + view.<error descr="Cannot resolve symbol 'y'">y</error> - referencey;
					String itemname = ("list" == classname) ? "item" :
						(("table" == classname) ? "row" : "node");
					String subitem = ("tree" == classname) ? "node" : null;
					for (Object item = get(component, itemname); item != null;) {
						<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> r = getRectangle(item, "bounds");
						if (my < r.<error descr="Cannot resolve symbol 'y'">y</error> + r.<error descr="Cannot resolve symbol 'height'">height</error>) {
							if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_DRAGGED) { //!!!
								scrollToVisible(component, r.<error descr="Cannot resolve symbol 'x'">x</error>, r.<error descr="Cannot resolve symbol 'y'">y</error>, 0, r.<error descr="Cannot resolve symbol 'height'">height</error> - 1);
							}
							else if ("tree" == classname) {
								int mx = mousex + view.<error descr="Cannot resolve symbol 'x'">x</error> - referencex;
								if (mx < r.<error descr="Cannot resolve symbol 'x'">x</error>) {
									if ((mx >= r.<error descr="Cannot resolve symbol 'x'">x</error> - block) && (get(item, "node") != null)) {
										boolean expanded = getBoolean(item, "expanded", true);
										setBoolean(item, "expanded", !expanded, 	true);
										selectItem(component, item, "node", "node");
										setLead(component, get(component, "lead"), item);
										setFocus(component);
										validate(component);
										invoke(component, expanded ? "collapse" : "expand"); //item
									}
									break;
								}
							}
							if ((id != <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_DRAGGED) ||
									!getBoolean(item, "selected", false)) {
								select(component, item, itemname, subitem, shiftdown, controldown);
								if (id != <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_DRAGGED) {
									if (setFocus(component)) { repaint(component, classname, item); } //?
								}
							}
							break;
						}
						item = getNextItem(component, item, subitem);
					}
				}
		 	}
		}
		else if ("menubar" == classname) {
			Object selected = get(component, "selected");
			if (((id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_ENTERED) || (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_EXITED)) &&
					(part != null) && (selected == null)) {
				repaint(component, classname, part);
			}
			else if ((part != null) && ((selected == null) ?
				(id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_PRESSED) :
					((id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_ENTERED) || (id == DRAG_ENTERED)))) {
				set(component, "selected", part);
				popup(component, classname);
				repaint(component, classname, part);
			}
			else if ((id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_PRESSED) && (selected != null)) {
				closeup(component);
			}
			else if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_RELEASED) {
				if ((part != insidepart) &&
						((insidepart == null) || (getClass(insidepart) != "menu"))) {
					if ((insidepart != null) && getBoolean(insidepart, "enabled", true)) {
						if (getClass(insidepart) == "checkboxmenuitem") {
							changeCheck(insidepart, false);
						}
						else invoke(insidepart, "action");
					}
					closeup(component);
				}
			}
		}
		else if ("popupmenu" == classname) {
			if (part != null) {
				if ((id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_ENTERED) || (id == DRAG_ENTERED)) {
					set(component, "selected", part);
					popup(component, classname);
					repaint(component, classname, part);
				}
				else if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_RELEASED) {
					if ((insidepart == null) || (getClass(insidepart) != "menu")) {
						Object menubar = part;
						do {
							menubar = getParent(menubar);
						} while (getClass(menubar) != "menubar");
						if ((insidepart != null) && getBoolean(insidepart, "enabled", true)) {
							if (getClass(insidepart) == "checkboxmenuitem") {
								changeCheck(insidepart, false);
							}
							else invoke(insidepart, "action");
						}
						closeup(menubar);
					}
				}
				else if ((id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_EXITED) || (id == DRAG_EXITED)) {
					if (getClass(part) != "menu") {
						set(component, "selected", null);
					}
					repaint(component, classname, part);
				}
			}
		}
		else if ("dialog" == classname) {
			if (part == "header") {
				if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_PRESSED) {
					referencex = mousex; referencey = mousey;
					if (!getBoolean(component, "modal", false) &&
							(get(content, "component") != component)) {
						removeItemImpl(content, "component", component);
						insertItem(content, "component", component, 0);
						set(component, ":parent", content);
						repaint(component);
					}
				}
				else if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_DRAGGED) {
					<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> bounds = getRectangle(component, "bounds");
					int dx = mousex - referencex; int dy = mousey - referencey;
					repaint(component,
						bounds.<error descr="Cannot resolve symbol 'x'">x</error> + Math.min(0, dx), bounds.<error descr="Cannot resolve symbol 'y'">y</error> + Math.min(0, dy),
						bounds.<error descr="Cannot resolve symbol 'width'">width</error> + Math.abs(dx), bounds.<error descr="Cannot resolve symbol 'height'">height</error> + Math.abs(dy));
					bounds.<error descr="Cannot resolve symbol 'x'">x</error> += dx; bounds.<error descr="Cannot resolve symbol 'y'">y</error> += dy;
					referencex = mousex; referencey = mousey;
				}
			}
		}
	}

	/**
	 *
	 */
	private void setReference(Object component, int x, int y) {
		referencex = x; referencey = y;
		for (; component != null; component = getParent(component)) {
			<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> bounds = getRectangle(component, "bounds");
			referencex += bounds.<error descr="Cannot resolve symbol 'x'">x</error>; referencey += bounds.<error descr="Cannot resolve symbol 'y'">y</error>;
		}
	}

	/**
	 *
	 */
	private void select(Object component, Object row, String first,
			String child, boolean shiftdown, boolean controldown) {
		String selection = getString(component, "selection", "single");
		Object lead = null;
		if (shiftdown && (selection != "single") &&
				((lead = get(component, "lead")) != null)) {
			extend(component, lead, row, first, child);
		}
		else {
			if (controldown && (selection == "multiple")) {
				setBoolean(row, "selected",
					!getBoolean(row, "selected", false), false);
				repaint(component, null, row);
				invoke(component, "action");
				set(component, "anchor", null);
			}
			else if (controldown && getBoolean(row, "selected", false)) {
				for (Object item = row;
						item != null; item = getNextItem(component, item, child)) {
					if (setBoolean(item, "selected", false, false)) {
						repaint(component, null, item);
					}
				}
				invoke(component, "action");
				set(component, "anchor", null);
			}
			else {
				selectItem(component, row, first, child);
			}
		}
		setLead(component, (lead != null) ? lead : get(component, "lead"), row);
	}

	/**
	 *
	 */
	private Object getNextItem(Object component,
			Object item, String subitem) {
		if (subitem == null) { return get(item, ":next"); }
		Object next = get(item, subitem);
		if ((next == null) || !getBoolean(item, "expanded", true)) {
			while ((item != component) && ((next = get(item, ":next")) == null)) {
				item = getParent(item);
			}
		}
		return next;
	}

	/**
	 *
	 */
	private void processField(int x, int y, int clickcount,
			int id, Object component,
			Object part, boolean multiline, boolean hidden, int left) {
		if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_PRESSED) {
			setReference(component, 2 + left, 2);
			int mx = x - referencex;
			int my = 0;
			if (!multiline) {
				mx += getInteger(component, "offset", 0);
			} else {
				<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> view = getRectangle(component, ":view");
				mx += view.<error descr="Cannot resolve symbol 'x'">x</error> - 1;
				my = y - referencey + view.<error descr="Cannot resolve symbol 'y'">y</error> - 1;
			}
			int caretstart = getCaretLocation(component, mx, my, hidden);
			int caretend = caretstart;
			//java>
			if (clickcount > 1) {
				String text = getString(component, "text", "");
				while ((caretstart > 0) && ((clickcount == 2) ?
					Character.isLetterOrDigit(text.charAt(caretstart - 1)) :
						(text.charAt(caretstart - 1) != '\n'))) { caretstart--; }
				while ((caretend < text.length()) && ((clickcount == 2) ?
					Character.isLetterOrDigit(text.charAt(caretend)) :
						(text.charAt(caretend) != '\n'))) { caretend++; }
			}
			//<java
			setInteger(component, "start", caretstart, 0);
			setInteger(component, "end", caretend, 0);
			setFocus(component);
			validate(component); // caret check only
		}
		else if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_DRAGGED) {
			int mx = x - referencex;
			int my = 0;
			if (!multiline) {
				mx += getInteger(component, "offset", 0);
			} else {
				<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> view = getRectangle(component, ":view");
				mx += view.<error descr="Cannot resolve symbol 'x'">x</error> - 1;
				my = y - referencey + view.<error descr="Cannot resolve symbol 'y'">y</error> - 1;
			}
			int dragcaret = getCaretLocation(component, mx, my, hidden);
			if (dragcaret != getInteger(component, "end", 0)) {
				setInteger(component, "end", dragcaret, 0);
				validate(component); // caret check only
			}
		}
		//java>
		else if ((id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_ENTERED) && (mousepressed == null)) {
			setCursor(<error descr="Cannot resolve symbol 'Cursor'">Cursor</error>.getPredefinedCursor(<error descr="Cannot resolve symbol 'Cursor'">Cursor</error>.TEXT_CURSOR));
		}
		else if (((id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_EXITED) && (mousepressed == null)) ||
			((id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_RELEASED) &&
				((mouseinside != component) || (insidepart != null)))) {
			setCursor(<error descr="Cannot resolve symbol 'Cursor'">Cursor</error>.getPredefinedCursor(<error descr="Cannot resolve symbol 'Cursor'">Cursor</error>.DEFAULT_CURSOR));
		}
		//<java
	}

	/**
	 *
	 */
	private int getCaretLocation(Object component, int x, int y, boolean hidden) {
		String text = getString(component, "text", "");
		<error descr="Cannot resolve symbol 'FontMetrics'">FontMetrics</error> fm = getFontMetrics(getFont());
		for (int i = 0, j = 0; true; i = j + 1) {
			j = text.indexOf('\n', i);
			if ((j == -1) || y < fm.<error descr="Cannot resolve method 'getHeight()'">getHeight</error>()) {
				if (j == -1) { j = text.length(); }
				for (int k = i; k < j; k++) {
					int charwidth = fm.<error descr="Cannot resolve method 'charWidth(char)'">charWidth</error>(hidden ? '*' : text.charAt(k));
					if (x <= (charwidth / 2)) {
						return k;
					}
					x -= charwidth;
				}
				return j;
			}
			y -= fm.<error descr="Cannot resolve method 'getHeight()'">getHeight</error>();
		}
	}

	/**
	 *
	 */
	private boolean processScroll(int x, int y,
			int id, Object component, Object part) {
		if ((part == "up") || (part == "down") ||
				(part == "left") || (part == "right")) {
			if ((id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_ENTERED) ||
					(id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_EXITED) ||
					(id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_PRESSED) ||
					(id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_RELEASED)) {
				if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_PRESSED) {
					if (processScroll(component, part)) {
						setTimer(300L); return true;
					}
				}
				else {
					if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_RELEASED) { setTimer(0L); }
					repaint(component, null, part);
				}
			}
		}
		else if ((part == "uptrack") || (part == "downtrack") ||
				(part == "lefttrack") || (part == "righttrack")) {
			if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_PRESSED) {
				if (processScroll(component, part)) {
					setTimer(300L);
				}
			}
			else if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_RELEASED) {
				setTimer(0L);
			}
		}
		else if ((part == "vknob") || (part == "hknob")) {
			if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_PRESSED) {
				<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> port = getRectangle(component, ":port");
				<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> view = getRectangle(component, ":view");
				if (part == "hknob") {
					referencex = x -
						view.<error descr="Cannot resolve symbol 'x'">x</error> * (port.<error descr="Cannot resolve symbol 'width'">width</error> - 2 * block) / view.<error descr="Cannot resolve symbol 'width'">width</error>;
				} else {
					referencey = y -
						view.<error descr="Cannot resolve symbol 'y'">y</error> * (port.<error descr="Cannot resolve symbol 'height'">height</error> - 2 * block) / view.<error descr="Cannot resolve symbol 'height'">height</error>;
				}
			}
			else if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_DRAGGED) {
				<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> port = getRectangle(component, ":port");
				<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> view = getRectangle(component, ":view");
				if (part == "hknob") {
					int viewx = (x - referencex) *
						view.<error descr="Cannot resolve symbol 'width'">width</error> / (port.<error descr="Cannot resolve symbol 'width'">width</error> - 2 * block);
					viewx = Math.max(0, Math.min(viewx, view.<error descr="Cannot resolve symbol 'width'">width</error> - port.<error descr="Cannot resolve symbol 'width'">width</error> + 2));
					if (view.<error descr="Cannot resolve symbol 'x'">x</error> != viewx) {
						view.<error descr="Cannot resolve symbol 'x'">x</error> = viewx;
						repaint(component, null, "horizontal");
					}
				} else {
					int viewy = (y - referencey) *
						view.<error descr="Cannot resolve symbol 'height'">height</error> / (port.<error descr="Cannot resolve symbol 'height'">height</error> - 2 * block);
					viewy = Math.max(0, Math.min(viewy, view.<error descr="Cannot resolve symbol 'height'">height</error> - port.<error descr="Cannot resolve symbol 'height'">height</error> + 2));
					if (view.<error descr="Cannot resolve symbol 'y'">y</error> != viewy) {
						view.<error descr="Cannot resolve symbol 'y'">y</error> = viewy;
						repaint(component, null, "vertical");
					}
				}
			}
		}
		else if (part == "corner") {
				part = "corner"; // compiler bug
		}
		else {
			if (id == <error descr="Cannot resolve symbol 'MouseEvent'">MouseEvent</error>.MOUSE_PRESSED) {
				<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> port = getRectangle(component, ":port");
				setReference(component, port.<error descr="Cannot resolve symbol 'x'">x</error> + 1, port.<error descr="Cannot resolve symbol 'y'">y</error> + 1);
			}
			return false;
		}
		return true;
	}

	/**
	 *
	 */
	private boolean processScroll(Object component, Object part) {
		<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> view = getRectangle(component, ":view");
		<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> port = ((part == "left") || (part == "up")) ? null :
			getRectangle(component, ":port");
		int dx = 0; int dy = 0;
		if (part == "left") { dx = -10; }
		else if (part == "lefttrack") { dx = -port.<error descr="Cannot resolve symbol 'width'">width</error>; }
		else if (part == "right") { dx = 10; }
		else if (part == "righttrack") { dx = port.<error descr="Cannot resolve symbol 'width'">width</error>; }
		else if (part == "up") { dy = -10; }
		else if (part == "uptrack") { dy = -port.<error descr="Cannot resolve symbol 'height'">height</error>; }
		else if (part == "down") { dy = 10; }
		else if (part == "downtrack") { dy = port.<error descr="Cannot resolve symbol 'height'">height</error>; }
		if (dx != 0) {
			dx = (dx < 0) ? Math.max(-view.<error descr="Cannot resolve symbol 'x'">x</error>, dx) :
				Math.min(dx, view.<error descr="Cannot resolve symbol 'width'">width</error> - port.<error descr="Cannot resolve symbol 'width'">width</error> + 2 - view.<error descr="Cannot resolve symbol 'x'">x</error>);
		}
		else if (dy != 0) {
			dy = (dy < 0) ? Math.max(-view.<error descr="Cannot resolve symbol 'y'">y</error>, dy) :
				Math.min(dy, view.<error descr="Cannot resolve symbol 'height'">height</error> - port.<error descr="Cannot resolve symbol 'height'">height</error> + 2 - view.<error descr="Cannot resolve symbol 'y'">y</error>);
		}
		else return false;
		view.<error descr="Cannot resolve symbol 'x'">x</error> += dx; view.<error descr="Cannot resolve symbol 'y'">y</error> += dy;
		repaint(component, null, (dx != 0) ? "horizontal" : "vertical");
		return (((part == "left") || (part == "lefttrack")) && (view.<error descr="Cannot resolve symbol 'x'">x</error> > 0)) ||
			(((part == "right") || (part == "righttrack")) &&
				(view.<error descr="Cannot resolve symbol 'x'">x</error> < view.<error descr="Cannot resolve symbol 'width'">width</error> - port.<error descr="Cannot resolve symbol 'width'">width</error> + 2)) ||
			(((part == "up") || (part == "uptrack")) && (view.<error descr="Cannot resolve symbol 'y'">y</error> > 0)) ||
			(((part == "down") || (part == "downtrack")) &&
				(view.<error descr="Cannot resolve symbol 'y'">y</error> < view.<error descr="Cannot resolve symbol 'height'">height</error> - port.<error descr="Cannot resolve symbol 'height'">height</error> + 2));
	}

	/**
	 *
	 */
	private boolean processSpin(Object component, Object part) {
		String text = getString(component, "text", null);
		if (text != null) {
			try {
				String value = String.valueOf(
					Integer.parseInt(text) + ((part == "up") ? 1 : -1));
				setString(component, "text", value, null);
				setInteger(component, "start", value.length(), 0);
				setInteger(component, "end", 0, 0);
				repaint(component, "spinbox", null);
				invoke(component, "action");
				return true;
			} catch (NumberFormatException nfe) {}
		}
		return false;
	}

	//java>
	/*public void setEventHandler(Object component, Object eventhandler) {
		set(component, ":handler", eventhandler);
	}*/

	/**
	 *
	 */
	private void invoke(Object component, String event) {
		<error descr="Cannot resolve symbol 'Method'">Method</error> method = (<error descr="Cannot resolve symbol 'Method'">Method</error>) get(component, event);
		if (method != null) {
			try {
				method.<error descr="Cannot resolve method 'invoke(Thinlet, null)'">invoke</error>(this, null);
			} catch (<error descr="Cannot resolve symbol 'InvocationTargetException'">InvocationTargetException</error> ite) {
				ite.<error descr="Cannot resolve method 'getTargetException()'">getTargetException</error>().printStackTrace();
			} catch (Exception exc) {
				exc.printStackTrace();
			}
		}
	}
	//<java
	/*midp
	private void invoke(Object component, String event) {
			String action = (String) get(component, event);
			if (action != null) { handle(component, action); }
	}
	protected void handle(Object source, String action) {
	}
	midp*/

	/**
	 *
	 */
	private boolean findComponent(Object component, int x, int y) {
		if (component == content) {
			mouseinside = insidepart = null;
			mousex = x; mousey = y;
		}
		if (!getBoolean(component, "visible", true)) { return false; }
		<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> bounds = getRectangle(component, "bounds");
		if ((bounds == null) || !(bounds.<error descr="Cannot resolve method 'contains(int, int)'">contains</error>(x, y))) { return false; } //java
		//midp if ((bounds == null) || (x < bounds.x) || (x - bounds.x >= bounds.width) ||
		//midp 	(y < bounds.y) || (y - bounds.y >= bounds.height)) { return false; }
		mouseinside = component;
		x -= bounds.<error descr="Cannot resolve symbol 'x'">x</error>; y -= bounds.<error descr="Cannot resolve symbol 'y'">y</error>;
		String classname = getClass(component);

		if ("combobox" == classname) {
			if (getBoolean(component, "editable", true) && (x <= bounds.<error descr="Cannot resolve symbol 'width'">width</error> - block)) {
				<error descr="Cannot resolve symbol 'Image'">Image</error> icon = getIcon(component, "icon", null);
				insidepart = ((icon != null) && (x <= 2 + icon.<error descr="Cannot resolve method 'getWidth(Thinlet)'">getWidth</error>(this))) ?
					"icon" : null;
			} else {
				insidepart = "down";
			}
		}
		else if ("combolist" == classname) {
			if (!findScrollPane(component, x, y, bounds)) {
				y += getRectangle(component, ":view").<error descr="Cannot resolve symbol 'y'">y</error>;
				for (Object choice = get(get(component, "combobox"), "choice");
						choice != null; choice = get(choice, ":next")) {
					<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> r = getRectangle(choice, "bounds");
					if ((y >= r.<error descr="Cannot resolve symbol 'y'">y</error>) && (y < r.<error descr="Cannot resolve symbol 'y'">y</error> + r.<error descr="Cannot resolve symbol 'height'">height</error>)) {
						insidepart = choice; break;
					}
				}
			}
		}
		else if ("textarea" == classname) {
			findScrollPane(component, x, y, bounds);
		}
		else if ("tabbedpane" == classname) {
			Object tabcontent = getItemImpl(component,
				"component", getInteger(component, "selected", 0));
			if ((tabcontent == null) || !findComponent(tabcontent, x, y)) {
				for (Object comp = get(component, "tab");
						comp != null; comp = get(comp, ":next")) {
					<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> r = getRectangle(comp, "bounds");
					if (r.<error descr="Cannot resolve method 'contains(int, int)'">contains</error>(x, y)) { //java
					//midp if ((x >= r.x) && (x - r.x < r.width) && (y >= r.y) && (y - r.y < r.height)) {
						insidepart = comp; break;
					}
				}
			}
		}
		else if (("panel" == classname) || ("desktop" == classname) ||
				("dialog" == classname)) {
			if (("dialog" == classname) &&
					(y < 4 + getInteger(component, "titleheight", 0))) {
				insidepart = "header";
			} else {
				for (Object comp = get(component, "component");
						comp != null; comp = get(comp, ":next")) {
					if (findComponent(comp, x, y)) { break; }
					if (("desktop" == classname) &&
							getBoolean(comp, "modal", false)) { break; } // && dialog
				}
			}
		}
		else if ("spinbox" == classname) {
			insidepart = (x <= bounds.<error descr="Cannot resolve symbol 'width'">width</error> - block) ? null :
				((y <= bounds.<error descr="Cannot resolve symbol 'height'">height</error> / 2) ? "up" : "down");
		}
		else if ("splitpane" == classname) {
			Object comp1 = get(component, "component");
			if (comp1 != null) {
				if (!findComponent(comp1, x, y)) {
					Object comp2 = get(comp1, ":next");
					if (comp2 != null) {
						findComponent(comp2, x, y);
					}
				}
			}
		}
		else if ("list" == classname) {
			findScrollPane(component, x, y, bounds);
		}
		else if ("table" == classname) {
			findScrollPane(component, x, y, bounds);
		}
		else if ("tree" == classname) {
			findScrollPane(component, x, y, bounds);
		}
		else if ("menubar" == classname) {
			for (Object menu = get(component, "menu");
					menu != null; menu = get(menu, ":next")) {
				<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> r = getRectangle(menu, "bounds");
				if ((x >= r.<error descr="Cannot resolve symbol 'x'">x</error>) && (x < r.<error descr="Cannot resolve symbol 'x'">x</error> + r.<error descr="Cannot resolve symbol 'width'">width</error>)) {
					insidepart = menu; break;
				}
			}
		}
		else if ("popupmenu" == classname) {
			for (Object menu = get(get(component, "menu"), "menu");
					menu != null; menu = get(menu, ":next")) {
				<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> r = getRectangle(menu, "bounds");
				if ((y >= r.<error descr="Cannot resolve symbol 'y'">y</error>) && (y < r.<error descr="Cannot resolve symbol 'y'">y</error> + r.<error descr="Cannot resolve symbol 'height'">height</error>)) {
					insidepart = menu; break;
				}
			}
		}
		return true;
	}

	/**
	 *
	 */
	private boolean findScrollPane(Object component,
			int x, int y, <error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> bounds) {
		<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> port = getRectangle(component, ":port");
		if ((x < port.<error descr="Cannot resolve symbol 'x'">x</error>) || (y < port.<error descr="Cannot resolve symbol 'y'">y</error>) ||
				((x >= port.<error descr="Cannot resolve symbol 'x'">x</error> + port.<error descr="Cannot resolve symbol 'width'">width</error>) && (y >= port.<error descr="Cannot resolve symbol 'y'">y</error> + port.<error descr="Cannot resolve symbol 'height'">height</error>))) {
			insidepart = "corner";
		}
		else if ((x >= port.<error descr="Cannot resolve symbol 'x'">x</error> + port.<error descr="Cannot resolve symbol 'width'">width</error>) || (y >= port.<error descr="Cannot resolve symbol 'y'">y</error> + port.<error descr="Cannot resolve symbol 'height'">height</error>)) {
			boolean horizontal = (y >= port.<error descr="Cannot resolve symbol 'y'">y</error> + port.<error descr="Cannot resolve symbol 'height'">height</error>);
			int p = horizontal ? (x - port.<error descr="Cannot resolve symbol 'x'">x</error>) : (y - port.<error descr="Cannot resolve symbol 'y'">y</error>);
			int portsize = horizontal ? port.<error descr="Cannot resolve symbol 'width'">width</error> : port.<error descr="Cannot resolve symbol 'height'">height</error>;
			int button = Math.min(block, portsize / 2);
			if (p < button) {
				insidepart = horizontal ? "left" : "up";
			}
			else if (p >= portsize - button) {
				insidepart = horizontal ? "right" : "down";
			}
			else {
				<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> view = getRectangle(component, ":view");
				int viewp = horizontal ? view.<error descr="Cannot resolve symbol 'x'">x</error> : view.<error descr="Cannot resolve symbol 'y'">y</error>;
				int viewsize = horizontal ? view.<error descr="Cannot resolve symbol 'width'">width</error> : view.<error descr="Cannot resolve symbol 'height'">height</error>;
				int track = portsize - (2 * button);
				int knob = Math.min(track,
					Math.max(track * (portsize - 2) / viewsize, 6));
				int decrease = viewp * (track - knob) / (viewsize - portsize + 2);
				if (p < button + decrease) {
					insidepart = horizontal ? "lefttrack" : "uptrack";
				}
				else if (p < button + decrease + knob) {
					insidepart = horizontal ? "hknob" : "vknob";
				}
				else {
					insidepart = horizontal ? "righttrack" : "downtrack";
				}
			}
		}
		else { return false; }
		return true;
	}

	/**
	 *
	 */
	private void repaint(Object component, Object classname, Object part) {
		<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> b = getRectangle(component, "bounds");
		if ((classname == "combobox") || (classname == "spinbox")) {
			boolean down = (part == "up") || (part == "down"); // else text
			repaint(component, down ? (b.<error descr="Cannot resolve symbol 'x'">x</error> + b.<error descr="Cannot resolve symbol 'width'">width</error> - block) : b.<error descr="Cannot resolve symbol 'x'">x</error>, b.<error descr="Cannot resolve symbol 'y'">y</error>,
				down ? block : (b.<error descr="Cannot resolve symbol 'width'">width</error> - block), b.<error descr="Cannot resolve symbol 'height'">height</error>);
		}
		//else if (classname == "dialog") {}
			//int titleheight = getInteger(component, "titleheight", 0);
		//else if (classname == "splitpane") {}
		else if ((classname == "tabbedpane") ||
				(classname == "menubar") || (classname == "popupmenu")) {
			<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> r = getRectangle(part, "bounds");
			repaint(component, b.<error descr="Cannot resolve symbol 'x'">x</error> + r.<error descr="Cannot resolve symbol 'x'">x</error>, b.<error descr="Cannot resolve symbol 'y'">y</error> + r.<error descr="Cannot resolve symbol 'y'">y</error>,
				(classname == "popupmenu") ? b.<error descr="Cannot resolve symbol 'width'">width</error> : r.<error descr="Cannot resolve symbol 'width'">width</error>, r.<error descr="Cannot resolve symbol 'height'">height</error>);
		}
		else //if ((classname == "combolist") || (classname == "textarea") ||
				{//(classname == "list") || (classname == "table") || (classname == "tree")) {
			<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> port = getRectangle(component, ":port");
			if (part == "left") {
				repaint(component, b.<error descr="Cannot resolve symbol 'x'">x</error> + port.<error descr="Cannot resolve symbol 'x'">x</error>, b.<error descr="Cannot resolve symbol 'y'">y</error> + b.<error descr="Cannot resolve symbol 'height'">height</error> - block, block, block);
			}
			else if (part == "right") {
				repaint(component, b.<error descr="Cannot resolve symbol 'x'">x</error> + port.<error descr="Cannot resolve symbol 'x'">x</error> + port.<error descr="Cannot resolve symbol 'width'">width</error> - block, b.<error descr="Cannot resolve symbol 'y'">y</error> + b.<error descr="Cannot resolve symbol 'height'">height</error> - block, block, block);
			}
			else if (part == "up") {
				repaint(component, b.<error descr="Cannot resolve symbol 'x'">x</error> + b.<error descr="Cannot resolve symbol 'width'">width</error> - block, b.<error descr="Cannot resolve symbol 'y'">y</error> + port.<error descr="Cannot resolve symbol 'y'">y</error>, block, block);
			}
			else if (part == "down") {
				repaint(component, b.<error descr="Cannot resolve symbol 'x'">x</error> + b.<error descr="Cannot resolve symbol 'width'">width</error> - block, b.<error descr="Cannot resolve symbol 'y'">y</error> + port.<error descr="Cannot resolve symbol 'y'">y</error> + port.<error descr="Cannot resolve symbol 'height'">height</error> - block, block, block);
			}
			else if (part == "horizontal") { // horizontaly center part
				repaint(component, b.<error descr="Cannot resolve symbol 'x'">x</error> + port.<error descr="Cannot resolve symbol 'x'">x</error>, b.<error descr="Cannot resolve symbol 'y'">y</error>, port.<error descr="Cannot resolve symbol 'width'">width</error>, b.<error descr="Cannot resolve symbol 'height'">height</error>);
			}
			else if (part == "vertical") {
				repaint(component, b.<error descr="Cannot resolve symbol 'x'">x</error>, b.<error descr="Cannot resolve symbol 'y'">y</error> + port.<error descr="Cannot resolve symbol 'y'">y</error>, b.<error descr="Cannot resolve symbol 'width'">width</error>, port.<error descr="Cannot resolve symbol 'height'">height</error>);
			}
			else if (part == "text") { //textarea
				repaint(component, b.<error descr="Cannot resolve symbol 'x'">x</error> + port.<error descr="Cannot resolve symbol 'x'">x</error>, b.<error descr="Cannot resolve symbol 'y'">y</error> + port.<error descr="Cannot resolve symbol 'y'">y</error>, port.<error descr="Cannot resolve symbol 'width'">width</error>, port.<error descr="Cannot resolve symbol 'height'">height</error>);
			}
			else {
				<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> view = getRectangle(component, ":view");
				<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> r = getRectangle(part, "bounds");
				if ((r.<error descr="Cannot resolve symbol 'y'">y</error> + r.<error descr="Cannot resolve symbol 'height'">height</error> >= view.<error descr="Cannot resolve symbol 'y'">y</error>) && (r.<error descr="Cannot resolve symbol 'y'">y</error> <= view.<error descr="Cannot resolve symbol 'y'">y</error> + port.<error descr="Cannot resolve symbol 'height'">height</error>)) {
					repaint(component, b.<error descr="Cannot resolve symbol 'x'">x</error> + port.<error descr="Cannot resolve symbol 'x'">x</error>, b.<error descr="Cannot resolve symbol 'y'">y</error> + port.<error descr="Cannot resolve symbol 'y'">y</error> - view.<error descr="Cannot resolve symbol 'y'">y</error> + 1 + r.<error descr="Cannot resolve symbol 'y'">y</error>,
						port.<error descr="Cannot resolve symbol 'width'">width</error>, r.<error descr="Cannot resolve symbol 'height'">height</error>);
					//? need cut item rectangle above/bellow viewport
				}
			}
		}
	}

	/**
	 *
	 */
	private void validate(Object component) {
		repaint(component);
		<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> bounds = getRectangle(component, "bounds");
		bounds.<error descr="Cannot resolve symbol 'width'">width</error> = -1 * Math.abs(bounds.<error descr="Cannot resolve symbol 'width'">width</error>);
	}

	/**
	 *
	 */
	private void repaint(Object component) {
		<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> bounds = getRectangle(component, "bounds");
		if (bounds != null) {
			repaint(component, bounds.<error descr="Cannot resolve symbol 'x'">x</error>, bounds.<error descr="Cannot resolve symbol 'y'">y</error>, bounds.<error descr="Cannot resolve symbol 'width'">width</error>, bounds.<error descr="Cannot resolve symbol 'height'">height</error>);
		}
	}

	/**
	 *
	 */
	private void repaint(Object component, int x, int y, int width, int height) {
		while ((component = getParent(component)) != null) {
			<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> bounds = getRectangle(component, "bounds");
			x += bounds.<error descr="Cannot resolve symbol 'x'">x</error>;
			y += bounds.<error descr="Cannot resolve symbol 'y'">y</error>;
		}
		repaint(x, y, width, height);
	}

	/*private void clip(Graphics g,
			Rectangle clip, int x, int y, int width, int height) {
		int x1 = Math.max(clip.x, x);
		int y1 = Math.max(clip.y, y);
		int x2 = Math.min(clip.x + clip.width, x + width);
		int y2 = Math.min(clip.y + clip.height, y + height);
		g.setClip(x1, y1, x2 - x1, y2 - y1);
	}*/

	/**
	 *
	 */
	public boolean requestFocus(Object component) {
		if (checkFocusable(component, true)) {
			setFocus(component); return true;
		}
		return false;
	}

	/**
	 *
	 */
	private boolean setFocus(Object component) {
		if (!focusinside) { //java
			requestFocus(); //java
		} //java
		if (focusowner != component) {
			Object focused = focusowner;
			focusowner = component;
			if (focused != null) {
				//mouseEvent(null, FocusEvent.FOCUS_LOST, focused, null, null);
				repaint(focused);
				//focusGained(component);
			}
			return true;
		}
		return false;
	}

	/**
	 * @return next focusable component is found (not the first of the desktop/dialog)
	 */
	private boolean setNextFocusable(Object current, boolean outgo) {
		boolean consumed = true;
		for (Object next = null, component = current; true; component = next) {
			next = get(component, "component"); // check first subcomponent
			if (next == null) { next = get(component, ":next"); } // check next component
			while (next == null) { // find the next of the parents, or the topmost
				component = getParent(component); // current is not on the desktop
				if (component == null) { return false; }
				if ((component == content) || ((getClass(component) == "dialog") &&
						(!outgo  || getBoolean(component, "modal", false)))) {
					consumed = false; // find next focusable but does not consume event
					next = component; // the topmost (desktop or modal dialog)
				}
				else {
					next = get(component, ":next");
				}
			}
			if (next == current) { return false; } // one fucusable, no loop
			if (checkFocusable(next, false)) {
				setFocus(next);
				return consumed;
			}
		}
	}

	//java>
	/**
	 * @return previous focusable component is found (not the last of the desktop/dialog)
	 */
	private boolean setPreviousFocusable(Object component, boolean outgo) {
		for (int i = 0; i < 2; i++) { // 0 is backward direction
			Object previous = getPreviousFocusable(component, null, true, false, (i == 0), outgo);
			if (previous != null) {
				setFocus(previous);
				return (i == 0);
			}
		}
		return false;
	}

	/**
	 * For the starting component search its parent direction for a focusable component, and then
	 * its next component (if not search backward from the component).<br>
	 * For its parent components check its first component, the current one, and its parent direction
	 * (backward search), or its parent, then next component (forward direction).<br>
	 * For the rest components check the next, then the first subcomponent direction, and finally
	 * check whether the component is focusable.
	 */
	private Object getPreviousFocusable(Object component,
			Object block, boolean start, boolean upward, boolean backward, boolean outgo) {
		Object previous = null;
		if ((component != null) && (component != block)) {
			boolean go = ((getClass(component) != "dialog") ||
				(outgo && !getBoolean(component, "modal", false)));
			if (!start && !upward && go) {
				previous = getPreviousFocusable(get(component, ":next"), block, false, false, backward, outgo);
			}
			if ((previous == null) && ((upward && backward) || (!start && !upward))) {
				previous = getPreviousFocusable(get(component, "component"), block, false, false, backward, outgo);
				if ((previous == null) && checkFocusable(component, false)) {
					previous = component;
				}
			}
			if ((previous == null) && (start || upward) && go) {
				previous = getPreviousFocusable(getParent(component), component, false, true, backward, outgo);
			}
			if ((previous == null) && (start || upward) && !backward && go) {
				previous = getPreviousFocusable(get(component, ":next"), block, false, false, backward, outgo);
			}
		}
		return previous;
	}
	//<java

	/**
	 *
	 */
	private boolean checkFocusable(Object component, boolean forced) {
		String classname = getClass(component);
		//midp forced=true;
		if ((classname == "button") || (classname == "checkbox") ||
				(classname == "combobox") || (classname == "textfield") ||
				(classname == "passwordfield") || (classname == "textarea") ||
				(classname == "spinbox") || (classname == "slider") ||
				(classname == "list") || (classname == "table") || (classname == "tree") ||
				(classname == "tabbedpane") || (forced && (classname == "splitpane"))) {
			for (Object comp = component; comp != null;) {
				if (!getBoolean(comp, "enabled", true) || !getBoolean(comp, "visible", true)) {
					return false;
				}
				Object parent = getParent(comp);
				if ((getClass(parent) == "tabbedpane") && (getItemImpl(parent,
					"component", getInteger(parent, "selected", 0)) != comp)) { return false; }
				comp = parent;
			}
			return true;
		}
		return false;
	}

	/*if (cliparea == null) { add(cliparea = new TextArea()); }
	cliparea.setText(content);
	cliparea.selectAll();
	cliparea.dispatchEvent(new KeyEvent(this, KeyEvent.KEY_TYPED, 0L, KeyEvent.CTRL_MASK, 0, (char) 3));
	cliparea.selectAll();

	if (cliparea == null) { add(cliparea = new TextArea()); }
	cliparea.dispatchEvent(new KeyEvent(this, KeyEvent.KEY_TYPED, 0L, KeyEvent.CTRL_MASK, 0, (char) 22));
	cliparea.getText(); return cliparea.getText();*/

	// ----- ----- ----- ----- ----- ----- ----- ----- ----- ----- ----- -----

	/**
	 *
	 */
	public Object create(String classname) {
		for (int i = 0; i < dtd.length; i += 3) {
			if (dtd[i].equals(classname)) {
				return createImpl((String) dtd[i]);
			}
		}
		throw new IllegalArgumentException("unknown " + classname);
	}

	/**
	 * @return classname
	 */
	public String getClass(Object component) {
		return (String) get(component, ":class");
	}

	/**
	 *
	 */
	public Object getDesktop() {
		return content;
	}

	/**
	 *
	 */
	private Object createImpl(String classname) {
		return new Object[] { ":class", classname, null };
	}

	/**
	 *
	 */
	private boolean set(Object component, Object key, Object value) {
		Object[] previous = (Object[]) component;
		for (Object[] entry = previous; entry != null;
				entry = (Object[]) entry[2]) {
			if (entry[0] == key) {
				if (value != null) { // set the row's value
					Object oldvalue = entry[1];
					entry[1] = value;
					return !value.equals(oldvalue);
				}
				else { // remove the row
					previous[2] = entry[2];
					entry[2] = null;
					return true;
				}
			}
			previous = entry;
		}
		if (value != null) { // append a new row
			previous[2] = new Object[] { key, value, null };
			return true;
		}
		return false;
	}

	/**
	 *
	 */
	private Object get(Object component, Object key) {
		for (Object[] entry = (Object[]) component; entry != null;
				entry = (Object[]) entry[2]) {
			if (entry[0] == key) {
				return entry[1];
			}
		}
		return null;
	}

	/**
	 *
	 */
	public int getCount(Object component) {
		return getCount(component, null);
	}

	/**
	 *
	 */
	public int getCount(Object component, String key) {
		return getItemCountImpl(component, getComponentName(component, key));
	}

	/**
	 *
	 */
	public Object getParent(Object component) {
		return get(component, ":parent");
	}

	/**
	 * @return the first selected index or -1
	 */
	public int getSelectedIndex(Object component) {
		int i = 0;
		for (Object item = get(component, getComponentName(component, null));
				item != null; item = get(item, ":next")) {
			if (getBoolean(item, "selected", false)) { return i; }
			i++;
		}
		return -1;
	}

	/**
	 *
	 */
	public void removeAll(Object component) {
		removeAll(component, null);
	}

	/**
	 *
	 */
	public void removeAll(Object component, String key) {
		String list = getComponentName(component, key);
		if (get(component, list) != null) {
			set(component, list, null);
			update(component, "validate");
		}
	}

	/**
	 *
	 */
	private int getItemCountImpl(Object component, Object key) {
		int i = 0;
		for (component = get(component, key); component != null;
				component = get(component, ":next")) {
			i++;
		}
		return i;
	}

	/**
	 *
	 */
	public Object getItem(Object component, int index) {
		return getItem(component, null, index);
	}

	/**
	 *
	 */
	public Object[] getItems(Object component) {
		return getItems(component, null);
	}

	/**
	 *
	 */
	public Object getItem(Object component, String key, int index) {
		return getItemImpl(component, getComponentName(component, key), index);
	}

	/**
	 *
	 */
	public Object[] getItems(Object component, String key) {
		key = getComponentName(component, key);
		Object[] items = new Object[getItemCountImpl(component, key)];
		component = get(component, key);
		for (int i = 0; i < items.length; i++) {
			items[i] = component;
			component = get(component, ":next");
		}
		return items;
	}

	/**
	 *
	 */
	private Object getItemImpl(Object component, Object key, int index) {
		int i = 0;
		for (Object item = get(component, key);
				item != null; item = get(item, ":next")) {
			if (i == index) { return item; }
			i++;
		}
		return null;
	}

	/**
	 *
	 */
	private int getIndex(Object component, Object key, Object value) {
		int index = 0;
		for (Object item = get(component, key);
				item != null; item = get(item, ":next")) {
			if (value == item) { return index; }
			index++;
		}
		return -1;
	}

	/**
	 *
	 */
	public void add(Object component) {
		add(content, component, 0);
	}

	/**
	 *
	 */
	public void add(Object parent, Object component) {
		add(parent, component, -1);
	}

	/**
	 *
	 */
	public void add(Object parent, Object component, int index) {
		addImpl(parent, component, index);
		update(component, "validate");
		if (parent == content) {
			setNextFocusable(component, false);
		}
	}

	/**
	 *
	 */
	private void addItem(Object parent, Object key, Object component) {
		insertItem(parent, key, component, -1);
	}

	/**
	 *
	 */
	private void insertItem(Object parent,
			Object key, Object component, int index) {
		Object target = get(parent, key);
		if (index == -1) {
			while (target != null) {
				target = get(parent = target, key = ":next");
			}
		}
		else {
			for (int i = 0; i < index; i++) {
				target = get(parent = target, key = ":next");
			}
			set(component, ":next", get(parent, key));
		}
		set(parent, key, component);
	}

	/**
	 *
	 */
	public void remove(Object component) {
		Object parent = getParent(component);
		String parentclass = getClass(parent);
		String classname = getClass(component);
		String listkey = ("combobox" == parentclass) ? "choice" :
			(("tabbedpane" == parentclass) && ("tab" == classname)) ? "tab" :
			("list" == parentclass) ? "item" :
			(("table" == parentclass) && ("column" == classname)) ? "column" :
			(("table" == parentclass) && ("row" == classname)) ? "row" :
			("row" == parentclass) ? "cell" :
			(("tree" == parentclass) || ("node" == parentclass)) ? "node" :
			(("menubar" == parentclass) || ("menu" == parentclass)) ? "menu" :
			(("panel" == parentclass) || ("desktop" == parentclass) ||
				("splitpane" == parentclass) || ("dialog" == parentclass) ||
				("tabbedpane" == parentclass)) ? "component" : null;
		if (listkey == null) { throw new IllegalArgumentException("unknown " + classname); }
		update(component, "validate");
		removeItemImpl(parent, listkey, component);
		// reuest focus for its parent if the component (or subcomponent) is currently focused
		for (Object comp = focusowner; comp != null; comp = getParent(comp)) {
			if (comp == component) {
				setNextFocusable(parent, false); break;
			}
		}
	}

	/**
	 *
	 */
	private void removeItemImpl(Object parent, Object key, Object component) {
		Object target = get(parent, key);
		while (target != component) {
			target = get(parent = target, key = ":next"); // (target != null)
		}
		set(parent, key, get(target, ":next"));
		set(target, ":next", null);
		set(target, ":parent", null);
	}

	/*
	private void removeItemImpl(Object parent, String key, int index) {
		Object target = get(parent, key);
		for (int i = 0; i < index; i++) {
			target = get(parent = target, key = ":next");
		}
		set(parent, key, get(target, ":next"));
		set(target, ":next", null);
	}*/

	/**
	 *
	 */
	private String getComponentName(Object parent, Object classname) {
		String parentclass = getClass(parent);
		String compname = ("combobox" == parentclass) ? "choice" :
			("list" == parentclass) ? "item" :
			("row" == parentclass) ? "cell" :
			(("tree" == parentclass) || ("node" == parentclass)) ? "node" :
			(("menubar" == parentclass) || ("menu" == parentclass)) ? "menu" :
			(("panel" == parentclass) || ("desktop" == parentclass) ||
				("splitpane" == parentclass) || ("dialog" == parentclass)) ?
				"component" : null;
		if ((compname != null) && ((classname == null) ||
				compname.equals(classname))) { return compname; }
		if ("tabbedpane" == parentclass) {
			if ("tab".equals(classname)) { return "tab"; }
			if ((classname == null) || "component".equals(classname)) { return "component"; }
		}
		else if ("table" == parentclass) {
			if ("column".equals(classname)) { return "column"; }
			if ((classname == null) || "row".equals(classname)) { return "row"; }
		}
		throw new IllegalArgumentException("unknown " + classname);
	}

	/**
	 *
	 */
	public Object find(String name) {
		return find(content, name);
	}

	/**
	 *
	 */
	public Object find(Object component, String name) {
		if (name.equals(get(component, "name"))) { return component; }
		String classname = getClass(component);
		String childname = null; String childname2 = null;
		if (("panel" == classname) || ("desktop" == classname) ||
				("splitpane" == classname) || ("dialog" == classname)) { childname = "component"; }
		else if ("combobox" == classname) { childname = "choice"; }
		else if ("tabbedpane" == classname) { childname = "tab"; childname2 = "component"; }
		else if ("list" == classname) { childname = "item"; }
		else if ("table" == classname) { childname = "column"; childname2 = "row"; }
		else if ("row" == classname) { childname = "cell"; }
		else if (("tree" == classname) || ("node" == classname)) { childname = "node"; }
		else if (("menubar" == classname) || ("menu" == classname)) { childname = "menu"; }
		while (childname != null) {
			for (Object comp = get(component, childname);
					comp != null; comp = get(comp, ":next")) {
				Object found = find(comp, name);
				if (found != null) { return found; }
			}
			childname = childname2; childname2 = null;
		}
		return null;
	}

	// ----- ----- ----- ----- ----- ----- ----- ----- ----- ----- ----- -----

	/*public String toXML(Object component) {
		StringBuffer xml = new StringBuffer();
		Object[] entry = (Object[]) component;
		String classname = (String) entry[1];
		while ((entry = (Object[]) entry[2]) != null) {
			try {
				Object[] definition = getDefinition(component, (String) entry[0], null);
				if (definition != null) {
					xml.append(" " + entry[0] + "=\"" + entry[1] + "\"");
				}
			} catch (IllegalArgumentException exc) {}
		}
		return xml.toString();
	}*/

	/**
	 *
	 */
	public Object parse(String path) throws Exception {
		InputStream inputstream = null;
		try { //java
			//midp inputstream = getClass().getResourceAsStream(path);
			inputstream = getClass().getResourceAsStream(path); //java
			//System.out.println("> " + path + " " + inputstream);
		} catch (Throwable e) {} //java
		//if (inputstream == null) { // applet code
		//	inputstream = new URL(getCodeBase(), path).openStream();
		//}
		return parse(inputstream); //, Object handler
	}

	/**
	 *
	 */
	public Object parse(InputStream inputstream) throws Exception {
		return parse(inputstream, true);
	}

	/**
	 *
	 */
	protected void parseXML(InputStream inputstream) throws Exception {
		parse(inputstream, false);
	}

	/**
	 *
	 */
	protected void startElement(String name, Hashtable attributelist) {}

	/**
	 *
	 */
	protected void characters(String text) {}

	/**
	 *
	 */
	protected void endElement() {}

	/**
	 *
	 */
	private Object parse(InputStream inputstream, boolean validate) throws Exception {
		BufferedReader reader = new BufferedReader(new InputStreamReader(inputstream)); //java
		//midp InputStreamReader reader = new InputStreamReader(inputstream);
		Object[] parentlist = null;
		Object current = null;
		Hashtable attributelist = null;
		StringBuffer text = new StringBuffer();
		for (int c = reader.read(); c != -1;) {
			if (c == '<') {
				if ((c = reader.read()) == '/') { //endtag
					if (text.length() > 0) {
						if (text.charAt(text.length() - 1) == ' ') {
							text.setLength(text.length() - 1);
						}
						if (!validate) {
							characters(text.toString());
						}// else {
							//addContent(current, text.toString());
						//}
						text.setLength(0);
					}
					String tagname = (String) parentlist[2]; //getClass(current);
					for (int i = 0; i < tagname.length(); i++) { // current-tag
						if ((c = reader.read()) != tagname.charAt(i)) {
							throw new IllegalArgumentException(tagname);
						}
					}
					while (" \t\n\r".indexOf(c = reader.read()) != -1); // whitespace
					if (c != '>') throw new IllegalArgumentException(); // '>'
					c = reader.read();
					if (!validate) { endElement(); }
					if (parentlist[0] == null) {
						reader.close();
						return current;
					}
					current = parentlist[0];
					parentlist = (Object[]) parentlist[1];
				}
				else { //start or standalone tag
					boolean instruction = (c == '?'); // Processing Instructions
					if (c == '!') { while ((c = reader.read()) != '>'); continue; } // DOCTYPE
					if (instruction) { c = reader.read(); }
					text.setLength(0);
					boolean iscomment = false;
					while (">/ \t\n\r".indexOf(c) == -1) {
						text.append((char) c);
						if ((text.length() == 3) && (text.charAt(0) == '!') &&
								(text.charAt(1) == '-') && (text.charAt(2) == '-')) {
							int m = 0;
							while (true) {
								c = reader.read();
								if (c == '-') { m++; }
								else if ((c == '>') && (m >= 2)) { break; }
								else { m = 0; }
							}
							iscomment = true;
						}
						c = reader.read();
					}
					if (iscomment) { continue; }
					if (!instruction) {
						String tagname = text.toString();
						parentlist = new Object[] { current, parentlist, tagname };
						if (validate) {
							current = (current != null) ?
								addElement(current, tagname) : create(tagname);
						} else {
							current = tagname;
						}
					}
					text.setLength(0);
					while (true) {
						boolean whitespace = false;
						while (" \t\n\r".indexOf(c) != -1) {
							c = reader.read();
							whitespace = true;
						}
						if (c == '>') {
							if (instruction) throw new IllegalArgumentException(); // '?>'
							if (!validate) {
								startElement((String) current, attributelist); attributelist = null;
							}
							c = reader.read();
							break;
						}
						else if (c == '/') {
							if (instruction) throw new IllegalArgumentException(); // '?>'
							if ((c = reader.read()) != '>') {
								throw new IllegalArgumentException(); // '>'
							}
							if (!validate) {
								startElement((String) current, attributelist); attributelist = null;
								endElement();
							}
							if (parentlist[0] == null) {
								reader.close();
								return current;
							}
							current = parentlist[0];
							parentlist = (Object[]) parentlist[1];
							c = reader.read();
							break;
						}
						else if (instruction && (c == '?')) {
							if ((c = reader.read()) != '>') {
								throw new IllegalArgumentException(); // '>'
							}
							c = reader.read();
							break;
						}
						else if (whitespace) {
							while ("= \t\n\r".indexOf(c) == -1) {
								text.append((char) c);
								c = reader.read();
							}
							String key = text.toString();
							text.setLength(0);
							while (" \t\n\r".indexOf(c) != -1) c = reader.read();
							if (c != '=') throw new IllegalArgumentException();
							while (" \t\n\r".indexOf(c = reader.read()) != -1);
							char quote = (char) c;
							if ((c != '\"') && (c != '\'')) throw new IllegalArgumentException();
							while (quote != (c = reader.read())) {
								if (c == '&') {
									StringBuffer eb = new StringBuffer();
									while (';' != (c = reader.read())) { eb.append((char) c); }
									String entity = eb.toString();
									if ("lt".equals(entity)) { text.append('<'); }
									else if ("gt".equals(entity)) { text.append('>'); }
									else if ("amp".equals(entity)) { text.append('&'); }
									else if ("quot".equals(entity)) { text.append('"'); }
									else if ("apos".equals(entity)) { text.append('\''); }
									else if (entity.startsWith("#")) {
										text.append((char) Integer.parseInt(entity.substring(1)));
									}
									else throw new IllegalArgumentException("unknown " + "entity " + entity);
								}
								else text.append((char) c);
							}
							if (!instruction) {
								if (validate) {
									addAttribute(current, key, text.toString());
								} else {
									if (attributelist == null) { attributelist = new Hashtable(); }
									attributelist.put(key, text.toString());
								}
							}
							text.setLength(0);
							c = reader.read();
						}
						else throw new IllegalArgumentException();
					}
				}
			}
			else {
				if (" \t\n\r".indexOf(c) != -1) {
					if ((text.length() > 0) && (text.charAt(text.length() - 1) != ' ')) {
						text.append(' ');
					}
				}
				else {
					text.append((char) c);
				}
				c = reader.read();
			}
		}
		throw new IllegalArgumentException();
	}

	/**
	 * Convert entities.
	 */
	private static String convert(StringBuffer text) {
		return null;
	}

	/*private InputStream inputreader;
	private byte[] data;
	private int inputfrom = 0, inputto = 0;
	private int read() throws Exception {
		if (data == null) { data = new byte[1024]; }
		if (inputfrom >= inputto) {
			inputfrom = 0;
			inputto = inputreader.read(data);
		}
		inputfrom++;
		return data[inputfrom - 1];
	}*/

	/**
	 *
	 */
	private void addImpl(Object parent, Object component, int index) {
		String parentclass = getClass(parent);
		String classname = getClass(component);
		//System.out.println("add " + classname + " -> " + parentclass);
		if ((("combobox" == parentclass) && ("choice" == classname)) ||
					(("tabbedpane" == parentclass) && ("tab" == classname)) ||
					(("list" == parentclass) && ("item" == classname)) ||
					(("table" == parentclass) && (("column" == classname) || ("row" == classname))) ||
					(("row" == parentclass) && ("cell" == classname)) ||
					((("tree" == parentclass) || ("node" == parentclass)) && ("node" == classname)) ||
					(("menubar" == parentclass) && ("menu" == classname))) {
				classname = classname; // compiler bug
		}
		else if (("menu" == parentclass) && (("menu" == classname) || ("menuitem" == classname) ||
				("checkboxmenuitem" == classname) || ("separator" == classname))) {
			classname = "menu";
		}
		else if (("panel" == parentclass) || ("desktop" == parentclass) ||
				("splitpane" == parentclass) || ("dialog" == parentclass) ||
				("tabbedpane" == parentclass)) {
			while ("component" != classname) {
				String extendclass = null;
				for (int i = 0; i < dtd.length; i += 3) {
					if (classname == dtd[i]) {
						extendclass = (String) dtd[i + 1]; break;
					}
				}
				if (extendclass == null) throw new IllegalArgumentException(classname + " not component");
				classname = extendclass;
			}
		}
		else throw new IllegalArgumentException(classname + " add " + parentclass);
		insertItem(parent, (String) classname, component, index);
		set(component, ":parent", parent);
		//if (parent == content) System.out.println(getClass(parent) + ".add(" + getClass(component) + ") : " + classname);
	}

	/**
	 *
	 */
	private Object addElement(Object parent, String name) {
		//System.out.println("create '" + name + "'");
		Object component = create(name);
		addImpl(parent, component, -1);
		return component;
	}

	/**
	 *
	 */
	private void addAttribute(Object component, String key, String value) {
		//System.out.println("attribute '" + key + "'='" + value + "'");
		Object[] definition = getDefinition(component, key, null);
		key = (String) definition[1];
		if ("string" == definition[0]) {
			setString(component, key, value, (String) definition[3]);
		}
		else if ("choice" == definition[0]) {
			String[] values = (String[]) definition[3];
			setChoice(component, key, value, values, values[0]);
		}
		else if ("boolean" == definition[0]) {
			if ("true".equals(value)) {
				if (definition[3] == Boolean.FALSE) {
					set(component, key, Boolean.TRUE);
			 	}
			}
			else if ("false".equals(value)) {
				if (definition[3] == Boolean.TRUE) {
					set(component, key, Boolean.FALSE);
			 	}
			}
			else throw new IllegalArgumentException(value);
		}
		else if ("integer" == definition[0]) {
			set(component, key, Integer.valueOf(value));
		}
		else if ("icon" == definition[0]) {
			set(component, key, <error descr="'set(java.lang.Object, java.lang.Object, java.lang.Object)' in 'Thinlet' cannot be applied to '(java.lang.Object, java.lang.String, Image)'">getIcon(value)</error>);
		}
		else if ("method" == definition[0]) {
			try { //java
				set(component, key, <error descr="'set(java.lang.Object, java.lang.Object, java.lang.Object)' in 'Thinlet' cannot be applied to '(java.lang.Object, java.lang.String, java.lang.reflect.Method)'">getClass().getMethod(value, null)</error>); //java
			} catch (Exception exc) { System.err.println(value); exc.printStackTrace(); } //java
			//midp setMethod(component, key, value);
		}
		//java>
		else if ("bean" == definition[0]) {
			try {
				set(component, key, (Component) Class.forName(value).newInstance());
			} catch (Exception exc) { System.err.println(value); exc.printStackTrace(); }
		}
		//<java
		else throw new IllegalArgumentException((String) definition[0]);
	}

	/**
	 *
	 */
	private Object[] getDefinition(Object component, String key, String type) {
		Object classname = getClass(component);
		Object currentname = classname;
		while (classname != null) {
			for (int i = 0; i < dtd.length; i += 3) {
				if (dtd[i] == classname) {
					Object[][] attributes = (Object[][]) dtd[i + 2];
					for (int j = 0; j < attributes.length; j++) {
						if (attributes[j][1].equals(key)) {
							if ((type != null) && (type != attributes[j][0])) {
								throw new IllegalArgumentException(attributes[j][0].toString());
							}
							return attributes[j];
						}
					}
					classname = dtd[i + 1];
					break;
				}
			}
		}
		throw new IllegalArgumentException("unknown " + key + " " + type +
			" for " + currentname);
	}

	/**
	 *
	 */
	//private void addContent(Object component, String content) {
		//set(component, "content", content);
	//}

	// ----- ----- ----- ----- ----- ----- ----- ----- ----- ----- ----- -----

	/**
	 *
	 */
	public void setString(Object component, String key, String value) {
		Object[] definition = getDefinition(component, key, "string");
		if (setString(component, (String) definition[1],
				value, (String) definition[3])) {
			update(component, definition[2]);
		}
	}

	/**
	 *
	 */
	public String getString(Object component, String key) {
		Object[] definition = getDefinition(component, key, "string");
		return getString(component, (String) definition[1],
			(String) definition[3]);
	}

	/**
	 *
	 */
	public void setChoice(Object component, String key, String value) {
		Object[] definition = getDefinition(component, key, "choice");
		String[] values = (String[]) definition[3];
		if (setChoice(component, (String) definition[1],
				value, values, values[0])) {
			update(component, definition[2]);
		}
	}

	/**
	 *
	 */
	public String getChoice(Object component, String key) {
		Object[] definition = getDefinition(component, key, "choice");
		return getString(component, (String) definition[1],
			((String[]) definition[3])[0]);
	}

	/**
	 *
	 */
	public void setBoolean(Object component, String key, boolean value) {
		Object[] definition = getDefinition(component, key, "boolean");
		if (setBoolean(component, (String) definition[1],
				value, (definition[3] == Boolean.TRUE))) {
			update(component, definition[2]);
		}
	}

	/**
	 *
	 */
	public boolean getBoolean(Object component, String key) {
		Object[] definition = getDefinition(component, key, "boolean");
		return getBoolean(component, (String) definition[1],
			(definition[3] == Boolean.TRUE));
	}

	/**
	 *
	 */
	public void setInteger(Object component, String key, int value) {
		Object[] definition = getDefinition(component, key, "integer");
		if (setInteger(component, (String) definition[1],
				value, ((Integer) definition[3]).intValue())) {
			update(component, definition[2]);
		}
	}

	/**
	 *
	 */
	public int getInteger(Object component, String key) {
		Object[] definition = getDefinition(component, key, "integer");
		return getInteger(component, (String) definition[1],
			((Integer) definition[3]).intValue());
	}

	/**
	 *
	 */
	public void setIcon(Object component, String key, <error descr="Cannot resolve symbol 'Image'">Image</error> icon) {
		Object[] definition = getDefinition(component, key, "icon");
		if (set(component, (String) definition[1], <error descr="'set(java.lang.Object, java.lang.Object, java.lang.Object)' in 'Thinlet' cannot be applied to '(java.lang.Object, java.lang.String, Image)'">icon</error>)) {
			update(component, definition[2]);
		}
	}

	/**
	 *
	 */
	public <error descr="Cannot resolve symbol 'Image'">Image</error> getIcon(Object component, String key) {
		Object[] definition = getDefinition(component, key, "icon");
		return getIcon(component, (String) definition[1], (<error descr="Cannot resolve symbol 'Image'">Image</error>) definition[3]);
	}

	/**
	 *
	 */
	public void setMethod(Object component, String key, <error descr="Cannot resolve symbol 'Method'">Method</error> method) { //java
	//midp public void setMethod(Object component, String key, String method) {
		Object[] definition = getDefinition(component, key, "method");
		if (set(component, (String) definition[1], <error descr="'set(java.lang.Object, java.lang.Object, java.lang.Object)' in 'Thinlet' cannot be applied to '(java.lang.Object, java.lang.String, Method)'">method</error>)) {
			update(component, definition[2]);
		}
	}

	/**
	 *
	 */
	public <error descr="Cannot resolve symbol 'Method'">Method</error> getMethod(Object component, String key) { //java
	//midp public String getMethod(Object component, String key) {
		Object[] definition = getDefinition(component, key, "method");
		return (<error descr="Cannot resolve symbol 'Method'">Method</error>) get(component, (String) definition[1]); //java
		//midp return (String) get(component, (String) definition[1]);
	}

	/**
	 *
	 */
	private void update(Object component, Object mode) {
		if ("parent" == mode) {
			component = getParent(component);
			mode = "validate";
		}
		boolean firstpaint = true;
		int x = 0; int y = 0; int width = 0; int height = 0;
		while (component != null) {
			if (!getBoolean(component, "visible", true)) { break; }
			if ("paint" == mode) {//|| (firstpaint && (component == content))
				<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> bounds = getRectangle(component, "bounds");
				if (bounds == null) { return; }
				if (firstpaint) {
					x = bounds.<error descr="Cannot resolve symbol 'x'">x</error>; y = bounds.<error descr="Cannot resolve symbol 'y'">y</error>;
					width = Math.abs(bounds.<error descr="Cannot resolve symbol 'width'">width</error>); height = bounds.<error descr="Cannot resolve symbol 'height'">height</error>;
					firstpaint = false;
				} else {
					x += bounds.<error descr="Cannot resolve symbol 'x'">x</error>; y += bounds.<error descr="Cannot resolve symbol 'y'">y</error>;
				}
				if (component == content) {
					repaint(x, y, width, height);
				}
			}
			Object parent = getParent(component);
			String classname = getClass(parent);
			if ("combobox" == classname) {
				parent = get(parent, "combolist");
			}
			else if ("menu" == classname) {
				parent = get(parent, "popupmenu");
			}
			else if (("paint" == mode) && ("tabbedpane" == classname)) {
				if (getItemImpl(parent, "component",
						getInteger(parent, "selected", 0)) != component) { break; }
			}
			if (("layout" == mode) || (("validate" == mode) &&
					(("list" == classname) || ("table" == classname) ||
					("tree" == classname) || ("dialog" == classname) || (parent == content)))) {
				<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> bounds = getRectangle(parent, "bounds");
				if (bounds == null) { return; }
				bounds.<error descr="Cannot resolve symbol 'width'">width</error> = -1 * Math.abs(bounds.<error descr="Cannot resolve symbol 'width'">width</error>);
				mode = "paint";
			}
			component = parent;
		}
	}

	// ----- ----- ----- ----- ----- ----- ----- ----- ----- ----- ----- -----

	/**
	 *
	 */
	private boolean setString(Object component,
			String key, String value, String defaultvalue) {
		/*boolean changed = set(component, key, value);
		if (changed && ("name" == key)) {
			//for (Class cls = getClass(); cls != null; cls = cls.getSuperclass()) {
			Field[] fields = getClass().getDeclaredFields();
			for (int i = 0; i < fields.length; i++) {
				if ((fields[i].getType() == Object.class) &&
						fields[i].getName().equals(value)) {
					try {
						fields[i].set(this, component);
						return true;
					} catch (IllegalAccessException iae) {}
				}
			}
			//}
			try {
				getClass().getField(value).set(this, component);
			} catch (Exception exc) {}
		}
		return changed;*/
		return set(component, key, value);
	}

	/**
	 *
	 */
	private String getString(Object component,
			String key, String defaultvalue) {
		Object value = get(component, key);
		return (value == null) ? defaultvalue : (String) value;
	}

	/**
	 *
	 */
	private boolean setChoice(Object component,
			String key, String value, String[] values, String defaultvalue) {
		if (value == null) {
			return set(component, key, defaultvalue);
		}
		for (int i = 0; i < values.length; i++) {
			if (value.equals(values[i])) {
				return set(component, key, values[i]);
			}
		}
		throw new IllegalArgumentException("unknown " + value + " for " + key);
	}

	/**
	 *
	 */
	private boolean setIcon(Object component,
			String key, String path, <error descr="Cannot resolve symbol 'Image'">Image</error> defaultvalue) {
		return set(component, key, <error descr="'set(java.lang.Object, java.lang.Object, java.lang.Object)' in 'Thinlet' cannot be applied to '(java.lang.Object, java.lang.String, Image)'">(path != null) ? getIcon(path) : defaultvalue</error>);
	}

	/**
	 *
	 */
	private <error descr="Cannot resolve symbol 'Image'">Image</error> getIcon(Object component, String key, <error descr="Cannot resolve symbol 'Image'">Image</error> defaultvalue) {
		Object value = get(component, key);
		return (value == null) ? defaultvalue : (<error descr="Cannot resolve symbol 'Image'">Image</error>) value;
	}

	/**
	 *
	 */
	private boolean setBoolean(Object component,
			String key, boolean value, boolean defaultvalue) {
		return set(component, key, (value == defaultvalue) ? null :
			(value ? Boolean.TRUE : Boolean.FALSE));
	}

	/**
	 *
	 */
	private boolean getBoolean(Object component,
			String key, boolean defaultvalue) {
		Object value = get(component, key);
		return (value == null) ? defaultvalue : ((Boolean) value).booleanValue();
	}

	/**
	 *
	 */
	private boolean setInteger(Object component,
			String key, int value, int defaultvalue) {
		return set(component, key, (value == defaultvalue) ? null : new Integer(value));
	}

	/**
	 *
	 */
	private int getInteger(Object component, String key, int defaultvalue) {
		Object value = get(component, key);
		return (value == null) ? defaultvalue : ((Integer) value).intValue();
	}

	/**
	 *
	 */
	private void setRectangle(Object component,
			String key, int x, int y, int width, int height) {
		<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> rectangle = getRectangle(component, key);
		if (rectangle != null) {
			rectangle.<error descr="Cannot resolve symbol 'x'">x</error> = x; rectangle.<error descr="Cannot resolve symbol 'y'">y</error> = y;
			rectangle.<error descr="Cannot resolve symbol 'width'">width</error> = width; rectangle.<error descr="Cannot resolve symbol 'height'">height</error> = height;
		}
		else {
			set(component, key, new <error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error>(x, y, width, height)); //java
			//midp set(component, key, new int[] { width, height, x, y });
		}
	}

	/**
	 *
	 */
	private <error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error> getRectangle(Object component, String key) {
		return (<error descr="Cannot resolve symbol 'Rectangle'">Rectangle</error>) get(component, key); //java
		//midp return (int[]) get(component, key);
	}

	// ----- ----- ----- ----- ----- ----- ----- ----- ----- ----- ----- -----

	/**
	 *
	 */
	public <error descr="Cannot resolve symbol 'Image'">Image</error> getIcon(String path) {
		return getIcon(path, true);
	}

	/**
	 *
	 */
	public <error descr="Cannot resolve symbol 'Image'">Image</error> getIcon(String path, boolean preload) {
		if ((path == null) || (path.length() == 0)) {
			return null;
		}
		<error descr="Cannot resolve symbol 'Image'">Image</error> image = null; //(Image) imagepool.get(path);
		//midp try {
		//midp 	image = Image.createImage(path);
		//midp } catch (IOException ioe) {}
		//java>
		try {
			image = <error descr="Cannot resolve symbol 'Toolkit'">Toolkit</error>.getDefaultToolkit().getImage(getClass().getResource(path));
			//image = Toolkit.getDefaultToolkit().getImage(ClassLoader.getSystemResource(path));
		} catch (Throwable e) {}
		if (image == null) {
			try {
				InputStream is = getClass().getResourceAsStream(path);
				//InputStream is = ClassLoader.getSystemResourceAsStream(path);
				if (is != null) {
					byte[] data = new byte[is.available()];
					is.read(data, 0, data.length);
					image = getToolkit().<error descr="Cannot resolve method 'createImage(byte[])'">createImage</error>(data);
					is.close();
				}
			} catch (Throwable e) {}
		}
		//if (image == null) { // applet code
		//	try {
		//		image = getImage(getCodeBase(), path);
		//	} catch (Throwable e) {}
		//}
		if (preload && (image != null)) {
			<error descr="Cannot resolve symbol 'MediaTracker'">MediaTracker</error> mediatracker = new <error descr="Cannot resolve symbol 'MediaTracker'">MediaTracker</error>(this);
			mediatracker.<error descr="Cannot resolve method 'addImage(Image, int)'">addImage</error>(image, 1);
			try {
				mediatracker.<error descr="Cannot resolve method 'waitForID(int, int)'">waitForID</error>(1, 50);
			} catch (InterruptedException ie) { }
			//imagepool.put(path, image);
		}
		//<java
		return image;
	}

	/**
	 *
	 */
	public boolean destroy() {
		return true;
	}

	private static Object[] dtd;
	static {
		Integer integer_1 = new Integer(-1);
		Integer integer0 = new Integer(0);
		Integer integer1 = new Integer(1);
		String[] orientation = { "horizontal", "vertical" };
		String[] leftcenterright = { "left", "center", "right" };
		String[] selections = { "single", "interval", "multiple" };
		dtd = new Object[] {
			"component", null, new Object[][] {
				{ "string", "name", "", null },
				{ "boolean", "enabled", "paint", Boolean.TRUE },
				{ "boolean", "visible", "parent", Boolean.TRUE },
				{ "string", "tooltip", "", null },
				{ "integer", "width", "validate", integer0 },
				{ "integer", "height", "validate", integer0 },
				{ "integer", "colspan", "validate", integer1 },
				{ "integer", "rowspan", "validate", integer1 },
				{ "integer", "weightx", "validate", integer0 },
				{ "integer", "weighty", "validate", integer0 },
				{ "choice", "halign", "validate",
					new String[] { "fill", "center", "left", "right" } },
				{ "choice", "valign", "validate",
					new String[] { "fill", "center", "top", "bottom" } }
				// component class String null*
				// parent Object null
				// (bounds) Rectangle 0 0 0 0
			},
			"label", "component", new Object[][] {
				{ "string", "text", "validate", null },
				{ "icon", "icon", "validate", null },
			 	{ "choice", "alignment", "validate", leftcenterright },
			 	{ "integer", "mnemonic", "paint", integer_1 } },
			"button", "label", new Object[][] {
			 	{ "choice", "alignment", "validate", new String[] { "center", "left", "right" } },
				{ "method", "action", "", null },
				//{ "choice", "type", "", new String[] { "normal", "default", "cancel" } }
				},//...
			"checkbox", "label", new Object[][] {
				{ "boolean", "selected", "paint", Boolean.FALSE }, //...group
				{ "string", "group", "paint", null }, //...group
				{ "method", "action", "", null } },
			"combobox", "textfield", new Object[][] {
				{ "icon", "icon", "validate", null },
				{ "integer", "selected", "layout", integer_1 } },
			"choice", null, new Object[][] {
				{ "string", "name", "", null },
				{ "boolean", "enabled", "paint", Boolean.TRUE },
				{ "string", "text", "", null },
				{ "icon", "icon", "", null },
				{ "choice", "alignment", "", leftcenterright },
				{ "string", "tooltip", "", null } },
			"textfield", "component", new Object[][] {
				{ "string", "text", "layout", "" },
				{ "integer", "columns", "validate", integer0 },
				{ "boolean", "editable", "paint", Boolean.TRUE },
				{ "integer", "start", "layout", integer0 },
				{ "integer", "end", "layout", integer0 },
				{ "method", "action", "", null } },
			"passwordfield", "textfield", new Object[][] {},
			"textarea", "textfield", new Object[][] {
				{ "integer", "rows", "validate", integer0 },
				{ "boolean", "wrap", "layout", Boolean.FALSE } },
			"tabbedpane", "component", new Object[][] {
				{ "choice", "placement", "validate",
					new String[] { "top", "left", "bottom", "right" } },
				{ "integer", "selected", "paint", integer0 },
				{ "method", "action", "", null } }, //...focus
			"tab", "choice", new Object[][] {
				{ "integer", "mnemonic", "paint", integer_1 } },
			"panel", "component", new Object[][] {
				{ "integer", "columns", "validate", integer0 },
				{ "integer", "top", "validate", integer0 },
				{ "integer", "left", "validate", integer0 },
				{ "integer", "bottom", "validate", integer0 },
				{ "integer", "right", "validate", integer0 },
				{ "integer", "gap", "validate", integer0 } },
			"desktop", "component", new Object[][] {},
			"dialog", "panel", new Object[][] {
				{ "string", "text", "", null },
				{ "icon", "icon", "", null },
				{ "boolean", "modal", "", Boolean.FALSE } },
			"spinbox", "textfield", new Object[][] {}, //...
			"progressbar", "component", new Object[][] {
				{ "choice", "orientation", "validate", orientation },
				{ "integer", "minimum", "paint", integer0 }, //...checkvalue
				{ "integer", "maximum", "paint", new Integer(100) },
				{ "integer", "value", "paint", integer0 } },
				// change stringpainted
			"slider", "progressbar", new Object[][] {
				{ "integer", "unit", "", new Integer(5) },
				{ "integer", "block", "", new Integer(25) },
				{ "method", "action", "", null } },
				// minor/majortickspacing
				// inverted
				// labelincrement labelstart
			"splitpane", "component", new Object[][] {
				{ "choice", "orientation", "validate", orientation },
				{ "integer", "divider", "layout", integer_1 } },
			"list", "component", new Object[][] {
				{ "choice", "selection", "paint", selections },
				{ "method", "action", "", null } }, //...?
			"item", "choice", new Object[][] {
				{ "boolean", "selected", "", Boolean.FALSE } },
			"table", "component", new Object[][] {
				{ "choice", "selection", "paint", selections },
				{ "method", "action", "", null }
				/*{ "choice", "selection",
					new String[] { "singlerow", "rowinterval", "multiplerow",
						"cell", "cellinterval",
						"singlecolumn", "columninterval", "multiplecolumn" } }*/ },
			"column", "choice", new Object[][] {
				{ "integer", "width", "", new Integer(80) }},
			"row", null, new Object[][] {
				{ "boolean", "selected", "", Boolean.FALSE } },
			"cell", "choice", new Object[][] {},
			"tree", "component", new Object[][] {
				{ "choice", "selection", "paint", selections },
				{ "method", "action", "", null },
				{ "method", "expand", "", null },
				{ "method", "collapse", "", null } },
			"node", "choice", new Object[][] {
				{ "boolean", "selected", "", Boolean.FALSE },
				{ "boolean", "expanded", "", Boolean.TRUE } },
			"separator", "component", new Object[][] {},
			"menubar", "component", new Object[][] {},
			"menu", "choice", new Object[][] {
				{ "integer", "mnemonic", "paint", integer_1 } },
			"menuitem", "choice", new Object[][] {
				{ "string", "accelerator", "", null },
				{ "method", "action", "", null },
				{ "integer", "mnemonic", "paint", integer_1 }
				//... KeyStroke=keyCode+modifiers(SHIFT CTRL META ALT_MASK)
			},
			"checkboxmenuitem", "menuitem", new Object[][] {
				{ "boolean", "selected", "paint", Boolean.FALSE }, //...group
				{ "string", "group", "paint", null } }, //...group
			"bean", "component", new Object[][] {
				{ "bean", "bean", "", null }
			}
		};
	}
}
