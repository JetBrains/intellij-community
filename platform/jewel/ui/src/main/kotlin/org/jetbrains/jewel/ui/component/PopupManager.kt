package org.jetbrains.jewel.ui.component

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/**
 * Manages a popup visibility.
 *
 * Note: two instances are equals and share the same hashcode if:
 * * They have the same [name]
 * * The [isPopupVisible] value is the same
 *
 * @param name An optional name given to the instance.
 * @param onPopupVisibleChange A lambda to call when the popup visibility changes.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public class PopupManager(public val onPopupVisibleChange: (Boolean) -> Unit = {}, public val name: String? = null) {
    private val _isPopupVisible: MutableState<Boolean> = mutableStateOf(false)

    /** Indicates whether the popup is currently visible. */
    public val isPopupVisible: State<Boolean> = _isPopupVisible

    /** Toggle the popup visibility. */
    public fun togglePopupVisibility() {
        setPopupVisible(!_isPopupVisible.value)
    }

    /**
     * Set the popup visibility.
     *
     * @param visible true when the popup should be shown, false otherwise.
     */
    public fun setPopupVisible(visible: Boolean) {
        if (_isPopupVisible.value == visible) return
        _isPopupVisible.value = visible
        onPopupVisibleChange(visible)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PopupManager

        if (name != other.name) return false
        if (isPopupVisible.value != other.isPopupVisible.value) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name?.hashCode() ?: 0
        result = 31 * result + isPopupVisible.value.hashCode()
        return result
    }

    override fun toString(): String = "PopupManager(isPopupVisible=${isPopupVisible.value}, name=$name)"
}
