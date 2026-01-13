// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// This is a slightly modified version of test 'tests.detailed.MainFrame' from repository https://github.com/JetBrains/jcef.git
package com.intellij.internal.jcef.test.detailed.handler;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefAuthCallback;
import org.cef.callback.CefCallback;
import org.cef.handler.CefLoadHandler.ErrorCode;
import org.cef.handler.CefRequestHandler;
import org.cef.handler.CefResourceHandler;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.handler.CefResourceRequestHandlerAdapter;
import org.cef.misc.BoolRef;
import org.cef.network.CefPostData;
import org.cef.network.CefPostDataElement;
import org.cef.network.CefRequest;

import java.awt.Frame;
import java.util.HashMap;
import java.util.Vector;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.cef.security.CefSSLInfo;
import com.intellij.internal.jcef.test.detailed.dialog.CertErrorDialog;
import com.intellij.internal.jcef.test.detailed.dialog.PasswordDialog;
import org.jetbrains.annotations.ApiStatus;

@SuppressWarnings("ALL")
@ApiStatus.Internal
public class  RequestHandler extends CefResourceRequestHandlerAdapter implements CefRequestHandler {
    private final Frame owner_;

    public RequestHandler(Frame owner) {
        owner_ = owner;
    }

    @Override
    public boolean onBeforeBrowse(CefBrowser browser, CefFrame frame, CefRequest request,
            boolean user_gesture, boolean is_redirect) {
        CefPostData postData = request.getPostData();
        if (postData != null) {
            Vector<CefPostDataElement> elements = new Vector<CefPostDataElement>();
            postData.getElements(elements);
            for (CefPostDataElement el : elements) {
                int numBytes = el.getBytesCount();
                if (numBytes <= 0) continue;

                byte[] readBytes = new byte[numBytes];
                if (el.getBytes(numBytes, readBytes) <= 0) continue;

                String readString = new String(readBytes);
                if (readString.indexOf("ignore") > -1) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            JOptionPane.showMessageDialog(owner_,
                                    "The request was rejected because you've entered \"ignore\" into the form.");
                        }
                    });
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean onOpenURLFromTab(
            CefBrowser browser, CefFrame frame, String target_url, boolean user_gesture) {
        return false;
    }

    @Override
    public CefResourceRequestHandler getResourceRequestHandler(CefBrowser browser, CefFrame frame,
            CefRequest request, boolean isNavigation, boolean isDownload, String requestInitiator,
            BoolRef disableDefaultHandling) {
        return this;
    }

    @Override
    public boolean onBeforeResourceLoad(CefBrowser browser, CefFrame frame, CefRequest request) {
        // If you send a HTTP-POST request to http://www.google.com/
        // google rejects your request because they don't allow HTTP-POST.
        //
        // This test extracts the value of the test form.
        // (see "Show Form" entry within BrowserMenuBar)
        // and sends its value as HTTP-GET request to Google.
        if (request.getMethod().equalsIgnoreCase("POST")
                && request.getURL().equals("http://www.google.com/")) {
            String forwardTo = "http://www.google.com/#q=";
            CefPostData postData = request.getPostData();
            boolean sendAsGet = false;
            if (postData != null) {
                Vector<CefPostDataElement> elements = new Vector<CefPostDataElement>();
                postData.getElements(elements);
                for (CefPostDataElement el : elements) {
                    int numBytes = el.getBytesCount();
                    if (numBytes <= 0) continue;

                    byte[] readBytes = new byte[numBytes];
                    if (el.getBytes(numBytes, readBytes) <= 0) continue;

                    String readString = new String(readBytes).trim();
                    String[] stringPairs = readString.split("&");
                    for (String s : stringPairs) {
                        int startPos = s.indexOf('=');
                        if (s.startsWith("searchFor"))
                            forwardTo += s.substring(startPos + 1);
                        else if (s.startsWith("sendAsGet")) {
                            sendAsGet = true;
                        }
                    }
                }
                if (sendAsGet) postData.removeElements();
            }
            if (sendAsGet) {
                request.setFlags(0);
                request.setMethod("GET");
                request.setURL(forwardTo);
                request.setFirstPartyForCookies(forwardTo);
                HashMap<String, String> headerMap = new HashMap<>();
                request.getHeaderMap(headerMap);
                headerMap.remove("Content-Type");
                headerMap.remove("Origin");
                request.setHeaderMap(headerMap);
            }
        }
        return false;
    }

    @Override
    public CefResourceHandler getResourceHandler(
            CefBrowser browser, CefFrame frame, CefRequest request) {
        // the non existing domain "foo.bar" is handled by the ResourceHandler implementation
        // E.g. if you try to load the URL http://www.foo.bar, you'll be forwarded
        // to the ResourceHandler class.
        if (request.getURL().endsWith("foo.bar/")) {
            return new ResourceHandler();
        }

        if (request.getURL().endsWith("seterror.test/")) {
            return new ResourceSetErrorHandler();
        }

        return null;
    }

    @Override
    public boolean getAuthCredentials(CefBrowser browser, String origin_url, boolean isProxy,
            String host, int port, String realm, String scheme, CefAuthCallback callback) {
        SwingUtilities.invokeLater(new PasswordDialog(owner_, callback));
        return true;
    }

    @Override
    public boolean onCertificateError(
            CefBrowser browser, ErrorCode cert_error, String request_url, CefSSLInfo sslInfo,
            CefCallback callback) {
        SwingUtilities.invokeLater(new CertErrorDialog(owner_, cert_error, request_url, callback));
        return true;
    }

    @Override
    public void onRenderProcessTerminated(
            CefBrowser browser, TerminationStatus status, int error_code, String error_string) {
    }
}
