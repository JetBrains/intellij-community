// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// This is a slightly modified version of test 'tests.detailed.MainFrame' from repository https://github.com/JetBrains/jcef.git
package com.intellij.internal.jcef.test.detailed.ui;

import org.cef.OS;
import org.cef.browser.CefBrowser;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URI;
import java.net.URISyntaxException;

@ApiStatus.Internal
public class  ControlPanel extends JPanel {
    private final JButton backButton_;
    private final JButton forwardButton_;
    private final JButton reloadButton_;
    private final JTextField address_field_;
    private final JLabel zoom_label_;
    private double zoomLevel_ = 0;
    private final CefBrowser browser_;

    public ControlPanel(CefBrowser browser) {
        assert browser != null;
        browser_ = browser;

        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

        add(Box.createHorizontalStrut(5));
        add(Box.createHorizontalStrut(5));

        backButton_ = new JButton("Back");
        backButton_.setFocusable(false);
        backButton_.setAlignmentX(LEFT_ALIGNMENT);
        backButton_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                browser_.goBack();
            }
        });
        add(backButton_);
        add(Box.createHorizontalStrut(5));

        forwardButton_ = new JButton("Forward");
        forwardButton_.setFocusable(false);
        forwardButton_.setAlignmentX(LEFT_ALIGNMENT);
        forwardButton_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                browser_.goForward();
            }
        });
        add(forwardButton_);
        add(Box.createHorizontalStrut(5));

        reloadButton_ = new JButton("Reload");
        reloadButton_.setFocusable(false);
        reloadButton_.setAlignmentX(LEFT_ALIGNMENT);
        reloadButton_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (reloadButton_.getText().equalsIgnoreCase("reload")) {
                    int mask = OS.isMacintosh() ? ActionEvent.META_MASK : ActionEvent.CTRL_MASK;
                    if ((e.getModifiers() & mask) != 0) {
                        browser_.reloadIgnoreCache();
                    } else {
                        browser_.reload();
                    }
                } else {
                    browser_.stopLoad();
                }
            }
        });
        add(reloadButton_);
        add(Box.createHorizontalStrut(5));

        JLabel addressLabel = new JLabel("Address:");
        addressLabel.setAlignmentX(LEFT_ALIGNMENT);
        add(addressLabel);
        add(Box.createHorizontalStrut(5));

        address_field_ = new JTextField(100);
        address_field_.setAlignmentX(LEFT_ALIGNMENT);
        address_field_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                browser_.loadURL(getAddress());
            }
        });
        add(address_field_);
        add(Box.createHorizontalStrut(5));

        JButton goButton = new JButton("Go");
        goButton.setFocusable(false);
        goButton.setAlignmentX(LEFT_ALIGNMENT);
        goButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                browser_.loadURL(getAddress());
            }
        });
        add(goButton);
        add(Box.createHorizontalStrut(5));

        JButton minusButton = new JButton("-");
        minusButton.setFocusable(false);
        minusButton.setAlignmentX(CENTER_ALIGNMENT);
        minusButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                browser_.setZoomLevel(--zoomLevel_);
                zoom_label_.setText(Double.valueOf(zoomLevel_).toString());
            }
        });
        add(minusButton);

        zoom_label_ = new JLabel("0.0");
        add(zoom_label_);

        JButton plusButton = new JButton("+");
        plusButton.setFocusable(false);
        plusButton.setAlignmentX(CENTER_ALIGNMENT);
        plusButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                browser_.setZoomLevel(++zoomLevel_);
                zoom_label_.setText(Double.valueOf(zoomLevel_).toString());
            }
        });
        add(plusButton);
    }

    public void update(
            CefBrowser browser, boolean isLoading, boolean canGoBack, boolean canGoForward) {
        if (browser == browser_) {
            backButton_.setEnabled(canGoBack);
            forwardButton_.setEnabled(canGoForward);
            reloadButton_.setText(isLoading ? "Abort" : "Reload");
        }
    }

    public String getAddress() {
        String address = address_field_.getText();
        // If the URI format is unknown "new URI" will throw an
        // exception. In this case we interpret the value of the
        // address field as search request. Therefore we simply add
        // the "search" scheme.
        try {
            address = address.replaceAll(" ", "%20");
            URI test = new URI(address);
            if (test.getScheme() != null) return address;
            if (test.getHost() != null && test.getPath() != null) return address;
            String specific = test.getSchemeSpecificPart();
            if (specific.indexOf('.') == -1)
                throw new URISyntaxException(specific, "No dot inside domain");
        } catch (URISyntaxException e1) {
            address = "search://" + address;
        }
        return address;
    }

    public void setAddress(CefBrowser browser, String address) {
        if (browser == browser_) address_field_.setText(address);
    }

    public JTextField getAddressField() {
        return address_field_;
    }
}
