package org.jetbrains.jewel.foundation

@InternalJewelApi
public object JewelConfigs {
    public val useCustomPopupRender: Boolean
        get() = System.getProperty("jewel.customPopupRender", "false").toBoolean()
}
