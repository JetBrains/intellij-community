// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.rulerguide;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

final class ComponentBoundsFinder {

    private BufferedImage image;
    private Result lastResult;

    public void update(Component component, Point point) {
        if (image == null || image.getWidth() < component.getWidth() || image.getHeight() <  component.getHeight()) {
            image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        }

        SneakyGraphics2D g2d = new SneakyGraphics2D((Graphics2D) image.getGraphics());
        component.paint(g2d);
        g2d.dispose();

        lastResult = new Result(g2d.getPOI(), component, point);
    }

    public Result getLastResult() {
        return lastResult;
    }

    public void dispose() {
        image = null;
        lastResult = null;
    }

    public static final class Result {
        private final List<ComponentBounds> bounds = new ArrayList<>();
        private final Component component;
        private final Point point;

        public Result(Collection<ComponentBounds> bounds, Component component, Point point) {
            this.bounds.addAll(bounds);
            this.bounds.sort(Comparator.comparingInt(b -> b.width * b.height));
            this.component = component;
            this.point = point;
        }

        public Collection<ComponentBounds> getBounds() {
            return bounds;
        }

        public Component getComponent() {
            return component;
        }

        public Point getPoint() {
            return point;
        }
    }
    
}
