// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test.rhtest;

import com.intellij.openapi.diagnostic.thisLogger
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLifeSpanHandler

internal class MyLifespanHandle: CefLifeSpanHandler {
    override fun onBeforePopup(
        browser: CefBrowser,
        frame: CefFrame,
        target_url: String,
        target_frame_name: String
    ): Boolean {
        thisLogger().info("in onBeforePopup")
        return true
    }

    override fun onAfterCreated(browser: CefBrowser) {
        thisLogger().info("in onAfterCreated")
        //val schemeHandlerFactory = MySchemaHandlerFactory()
        //CefApp.getInstance().registerSchemeHandlerFactory(
        //    schemeHandlerFactory.getSchema(), null, schemeHandlerFactory
        //)
        browser.loadURL("https://main/index.html")
    }

    override fun onAfterParentChanged(browser: CefBrowser) {
        thisLogger().info("in onAfterParentChanged")
    }

    override fun doClose(browser: CefBrowser): Boolean {
        thisLogger().info("in doClose")
        return true
    }

    override fun onBeforeClose(browser: CefBrowser) {
        thisLogger().info("in onBeforeClose")
    }

}
