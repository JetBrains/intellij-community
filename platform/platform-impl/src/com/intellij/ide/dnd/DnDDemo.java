/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.dnd;

import com.intellij.openapi.util.Pair;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.Point;

public class DnDDemo implements DnDEvent.DropTargetHighlightingType {
  public static void main(String[] args) {

    JFrame frame = new JFrame("DnD Demo");
    frame.getContentPane().setLayout(new BorderLayout());

    JPanel panel = new JPanel(new BorderLayout());
    final JTree source = new Tree();
    panel.add(source, BorderLayout.WEST);
    final DnDManager dndManager = new DnDManagerImpl(null);
    dndManager.registerSource(new DnDSource() {
      public boolean canStartDragging(DnDAction action, Point dragOrigin) {
        return true;
      }

      public DnDDragStartBean startDragging(DnDAction action, Point point) {
        return new DnDDragStartBean(source.getLastSelectedPathComponent().toString());
      }


      @Nullable
      public Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin) {
        return null;
      }

      public void dragDropEnd() {
      }

      public void dropActionChanged(final int gestureModifiers) {
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

      public void updateDraggedImage(Image image, Point dropPoint, Point imageOffset) {
      }
    };

    final DnDTarget delegee2 = new DnDTarget() {
      public boolean update(DnDEvent aEvent) {
        aEvent.setDropPossible("Delegee 2", new DropActionHandler() {
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

      public void updateDraggedImage(Image image, Point dropPoint, Point imageOffset) {
      }
    };

    dndManager.registerTarget(new DnDTarget() {
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


      public void updateDraggedImage(Image image, Point dropPoint, Point imageOffset) {
      }
    }, delegates);



    tabs.add("Delegates", delegates);

    final JPanel xy = new JPanel();
    dndManager.registerTarget(new DnDTarget() {
      public boolean update(DnDEvent aEvent) {
        aEvent.setDropPossible(true, "Drop to " + asXyString(aEvent));
        return false;
      }

      public void drop(DnDEvent aEvent) {
        System.out.println("Droppped to " + asXyString(aEvent));
      }

      public void cleanUpOnLeave() {
      }

      public void updateDraggedImage(Image image, Point dropPoint, Point imageOffset) {
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