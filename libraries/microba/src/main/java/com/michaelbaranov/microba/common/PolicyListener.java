package com.michaelbaranov.microba.common;

import java.util.EventListener;

/**
 * A listener that is notified of {@link PolicyEvent} events.
 * 
 * @author Michael Baranov
 * 
 */
public interface PolicyListener extends EventListener {

	/**
	 * Called when a {@link PolicyEvent} is fired.
	 * 
	 * @param event
	 */
	public void policyChanged(PolicyEvent event);
}
