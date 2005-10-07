/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.fabrique.ide.dnd;

import jetbrains.fabrique.openapi.ide.dnd.DnDTarget;
import jetbrains.fabrique.openapi.ide.dnd.DnDEvent;
import jetbrains.fabrique.openapi.ide.dnd.DnDAction;
import jetbrains.fabrique.openapi.ide.dnd.DropActionHandler;

import javax.swing.*;
import java.awt.*;

public class DnDDemo {
  public static void main(String[] args) {

    JFrame frame = new JFrame("DnD Demo");
    frame.getContentPane().setLayout(new BorderLayout());

    JPanel panel = new JPanel(new BorderLayout());
    final JTree source = new JTree();
    panel.add(source, BorderLayout.WEST);
    final DnDManager dndManager = new DnDManagerImpl();
    dndManager.register(new DnDSource() {
      public boolean canStartDragging(DnDAction action, Point dragOrigin) {
        return true;
      }

      public DnDEventImpl createEventForNewDragging(DnDAction action, Point point) {
        return new DnDEventImpl(dndManager, DnDAction.ADD, source.getLastSelectedPathComponent().toString());
      }
    }, source);


    JTabbedPane tabs = new JTabbedPane();

    JPanel delegates = new JPanel(new FlowLayout());
    final JLabel delegate1Label = new JLabel("Delegate 1");
    delegates.add(delegate1Label);
    final JLabel delegate2Label = new JLabel("Delegate 2");
    delegates.add(delegate2Label);
    final DnDTarget delegee1 = new DnDTarget() {
      public boolean update(DnDEvent aEvent) {
        aEvent.setDropPossible(true, "Delegee 1");
        aEvent.setHighlighting(delegate1Label, H_ARROWS | RECTANGLE);
        return false;
      }

      public void drop(DnDEvent aEvent) {
        System.out.println("Delegee 1 accepted drop");
      }

      public void cleanUpOnLeave() {
      }
    };

    final DnDTarget delegee2 = new DnDTarget() {
      public boolean update(DnDEvent aEvent) {
        aEvent.setDropPossible("Delegee 2", new DropActionHandler("Delegee 2 action") {
          public void performDrop(DnDEvent aEvent) {
            System.out.println("Delegee 2 accepted drop");
          }
        });
        aEvent.setHighlighting(delegate2Label, V_ARROWS | RECTANGLE);
        return false;
      }

      public void drop(DnDEvent aEvent) {

      }

      public void cleanUpOnLeave() {
      }
    };

    dndManager.register(new DnDTarget() {
      public boolean update(DnDEvent aEvent) {
        if (aEvent.getCurrentOverComponent() == delegate1Label) {
          return aEvent.delegateUpdateTo(delegee1);
        } else if (aEvent.getCurrentOverComponent() == delegate2Label) {
          return aEvent.delegateUpdateTo(delegee2);
        }

        aEvent.setDropPossible(false, "Nothing can be dropped here");
        return false;
      }

      public void drop(DnDEvent aEvent) {
        if (aEvent.getCurrentOverComponent() == delegate1Label) {
          aEvent.delegateDropTo(delegee1);
        }
      }

      public void cleanUpOnLeave() {
      }
    }, delegates);



    tabs.add("Delegates", delegates);

    final JPanel xy = new JPanel();
    dndManager.register(new DnDTarget() {
      public boolean update(DnDEvent aEvent) {
        aEvent.setDropPossible(true, "Drop to " + asXyString(aEvent));
        return false;
      }

      public void drop(DnDEvent aEvent) {
        System.out.println("Droppped to " + asXyString(aEvent));
      }

      public void cleanUpOnLeave() {
      }
    }, xy);

    tabs.add("XY drop", xy);

    panel.add(tabs, BorderLayout.CENTER);

    frame.getContentPane().add(panel, BorderLayout.CENTER);
    frame.setBounds(100, 100, 500, 500);
    frame.show();
  }

  public static String asXyString(DnDEvent aEvent) {
    return "[" + aEvent.getPoint().x + "," + aEvent.getPoint().y + "]";
  }
}