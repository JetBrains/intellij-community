// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.ui;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.StartupUiUtil;
import org.intellij.images.ImagesBundle;
import org.intellij.images.ImagesIcons;
import org.intellij.images.editor.ImageDocument;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * UI for {@link ThumbnailComponent}.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public class ThumbnailComponentUI extends ComponentUI {
    @NonNls
    private static final String DOTS = "...";

    private static final Color LINE_COLOR = new Color(0x8E, 0xA8, 0xCE);
    private static final Color PNG_COLOR = new Color(0x80, 0x00, 0x80);
    private static final Color GIF_COLOR = new Color(0x00, 0x80, 0x00);
    private static final Color JPG_COLOR = new Color(0x80, 0x80, 0x00);
    private static final Color BMP_COLOR = new Color(0x00, 0x00, 0x80);

    private static final ThumbnailComponentUI ui = new ThumbnailComponentUI();


    @Override
    public void paint(Graphics g, JComponent c) {
        ThumbnailComponent tc = (ThumbnailComponent) c;
        if (tc != null) {
            UISettings.setupAntialiasing(g);
            paintBackground(g, tc);

            if (tc.isDirectory()) {
                paintDirectory(g, tc);
            } else {
                paintImageThumbnail(g, tc);
            }

            // File name
            if (tc.isDirectory() || tc.getImageComponent().isFileNameVisible()) paintFileName(g, tc);
        }
    }

    private void paintDirectory(Graphics g, ThumbnailComponent tc) {
        // Paint directory icon
        ImagesIcons.ThumbnailDirectory.paintIcon(tc, g, 5, 5);

        int imagesCount = tc.getImagesCount();
        if (imagesCount > 0) {
            final String title = ImagesBundle.message("icons.count", imagesCount);

            Font font = getSmallFont();
            FontMetrics fontMetrics = g.getFontMetrics(font);
            g.setColor(Color.BLACK);
            g.setFont(font);
            g.drawString(title, 5 + (ImagesIcons.ThumbnailDirectory.getIconWidth() - fontMetrics.stringWidth(title)) / 2, ImagesIcons.ThumbnailDirectory
                                                                                                                            .getIconHeight() / 2 + fontMetrics.getAscent());
        }
    }

    private void paintImageThumbnail(Graphics g, ThumbnailComponent tc) {
        ImageComponent imageComponent = tc.getImageComponent();
        // Paint blank
        if (imageComponent.isFileSizeVisible()) ImagesIcons.ThumbnailBlank.paintIcon(tc, g, 5, 5);

        ImageDocument document = imageComponent.getDocument();
        BufferedImage image = document.getValue();
        if (image != null) {
            paintImage(g, tc);
        } else {
            paintError(g, tc);
        }

        if (imageComponent.isFileSizeVisible()) paintFileSize(g, tc);
    }

    private void paintBackground(Graphics g, ThumbnailComponent tc) {
        Dimension size = tc.getSize();
        g.setColor(tc.getBackground());
        g.fillRect(0, 0, size.width, size.height);
    }

    private void paintImage(Graphics g, ThumbnailComponent tc) {
        ImageComponent imageComponent = tc.getImageComponent();

        int blankHeight = ImagesIcons.ThumbnailBlank.getIconHeight();

        if (imageComponent.isFileSizeVisible()) {
            // Paint image info (and reduce height of text from available height)
            blankHeight -= paintImageCaps(g, imageComponent);
            // Paint image format (and reduce height of text from available height)
            blankHeight -= paintFormatText(tc, g);
        }

        // Paint image
        paintThumbnail(g, imageComponent, blankHeight);
    }

    private int paintImageCaps(Graphics g, ImageComponent imageComponent) {
        String description = imageComponent.getDescription();

        Font font = getSmallFont();
        FontMetrics fontMetrics = g.getFontMetrics(font);
        g.setColor(Color.BLACK);
        g.setFont(font);
        g.drawString(description, 8, 7 + fontMetrics.getAscent());

        return fontMetrics.getHeight();
    }

    private int paintFormatText(ThumbnailComponent tc, Graphics g) {
        Font font = getSmallFont().deriveFont(Font.BOLD);
        FontMetrics fontMetrics = g.getFontMetrics(font);

        String format = StringUtil.toUpperCase(tc.getFormat());
        int stringWidth = fontMetrics.stringWidth(format);
        int x = ImagesIcons.ThumbnailBlank.getIconWidth() - stringWidth + 2;
        int y = ImagesIcons.ThumbnailBlank.getIconHeight() - fontMetrics.getHeight() + 4;
        g.setColor(LINE_COLOR);
        g.drawLine(x - 3, y - 1, x + stringWidth + 1, y - 1);
        g.drawLine(x - 4, y, x - 4, y + fontMetrics.getHeight() - 1);
        g.setColor(getFormatColor(format));
        g.setFont(font);
        g.drawString(
                format,
                x,
                y + fontMetrics.getAscent()
        );

        return fontMetrics.getHeight();
    }

    private Color getFormatColor(String format) {
        if ("PNG".equals(format)) {
            return PNG_COLOR;
        } else if ("GIF".equals(format)) {
            return GIF_COLOR;
        } else if ("JPG".equals(format) || "JPEG".equals(format)) {
            return JPG_COLOR;
        } else if ("BMP".equals(format) || "WBMP".equals(format)) {
            return BMP_COLOR;
        }
        return Color.BLACK;
    }

    private void paintThumbnail(Graphics g, ImageComponent imageComponent, int blankHeight) {

        // Zoom image by available size
        int maxWidth = ImagesIcons.ThumbnailBlank.getIconWidth() - 10;
        int maxHeight = blankHeight - 10;

        BufferedImage image = imageComponent.getDocument().getValue();
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();

        if (imageWidth > maxWidth || imageHeight > maxHeight) {
            if (imageWidth > maxWidth) {
                double proportion = (double) maxWidth / (double) imageWidth;
                imageWidth = maxWidth;
                imageHeight = (int) ((double) imageHeight * proportion);
            }
            if (imageHeight > maxHeight) {
                double proportion = (double) maxHeight / (double) imageHeight;
                imageHeight = maxHeight;
                imageWidth = (int) ((double) imageWidth * proportion);
            }
        }

        imageComponent.setCanvasSize(imageWidth, imageHeight);
        Dimension size = imageComponent.getSize();

        int x = 5 + (ImagesIcons.ThumbnailBlank.getIconWidth() - size.width) / 2;
        int y = 5 + (ImagesIcons.ThumbnailBlank.getIconHeight() - size.height) / 2;


        imageComponent.paint(g.create(x, y, size.width, size.height));
    }

    private void paintFileName(Graphics g, ThumbnailComponent tc) {
        Font font = StartupUiUtil.getLabelFont();
        FontMetrics fontMetrics = g.getFontMetrics(font);

        g.setFont(font);
        g.setColor(tc.getForeground());

        String fileName = tc.getFileName();
        String title = fileName;
        while (fontMetrics.stringWidth(title) > ImagesIcons.ThumbnailBlank.getIconWidth() - 8) {
            title = title.substring(0, title.length() - 1);
        }

        if (fileName.equals(title)) {
            // Center
            g.drawString(fileName, 6 + (ImagesIcons.ThumbnailBlank.getIconWidth() - 2 - fontMetrics.stringWidth(title)) / 2, ImagesIcons.ThumbnailBlank
                                                                                                                               .getIconHeight() + 8 + fontMetrics.getAscent());
        } else {
            int dotsWidth = fontMetrics.stringWidth(DOTS);
            while (fontMetrics.stringWidth(title) > ImagesIcons.ThumbnailBlank.getIconWidth() - 8 - dotsWidth) {
                title = title.substring(0, title.length() - 1);
            }
            g.drawString(title + DOTS, 6, ImagesIcons.ThumbnailBlank.getIconHeight() + 8 + fontMetrics.getAscent());
        }
    }

    private void paintFileSize(Graphics g, ThumbnailComponent tc) {
        Font font = getSmallFont();
        FontMetrics fontMetrics = g.getFontMetrics(font);
        g.setColor(Color.BLACK);
        g.setFont(font);
        g.drawString(
                tc.getFileSizeText(),
                8,
                ImagesIcons.ThumbnailBlank.getIconHeight() + 4 - fontMetrics.getHeight() + fontMetrics.getAscent()
        );
    }

    private void paintError(Graphics g, ThumbnailComponent tc) {
        Font font = getSmallFont();
        FontMetrics fontMetrics = g.getFontMetrics(font);

        Messages.getErrorIcon().paintIcon(
          tc,
          g,
          5 + (ImagesIcons.ThumbnailBlank.getIconWidth() - Messages.getErrorIcon().getIconWidth()) / 2,
          5 + (ImagesIcons.ThumbnailBlank.getIconHeight() - Messages.getErrorIcon().getIconHeight()) / 2
        );

        // Error
        String error = getSubmnailComponentErrorString();
        g.setColor(JBColor.RED);
        g.setFont(font);
        g.drawString(error, 8, 8 + fontMetrics.getAscent());
    }

    private String getSubmnailComponentErrorString() {
        return ImagesBundle.message("thumbnails.component.error.text");
    }

    private static Font getSmallFont() {
        Font labelFont = StartupUiUtil.getLabelFont();
        return labelFont.deriveFont(labelFont.getSize2D() - 2.0f);
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
        Font labelFont = StartupUiUtil.getLabelFont();
        FontMetrics fontMetrics = c.getFontMetrics(labelFont);
        return new Dimension(
                ImagesIcons.ThumbnailBlank.getIconWidth() + 10,
                ImagesIcons.ThumbnailBlank.getIconHeight() + fontMetrics.getHeight() + 15
        );
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public static ComponentUI createUI(JComponent c) {
        return ui;
    }
}

