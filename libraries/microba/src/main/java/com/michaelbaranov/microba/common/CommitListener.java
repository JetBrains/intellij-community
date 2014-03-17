package com.michaelbaranov.microba.common;

import java.util.EventListener;

/**
 * A listener that is notified of {@link CommitEvent} events.
 * 
 * @author Michael Baranov
 * 
 */
public interface CommitListener extends EventListener {
	/**
	 * Called when a {@link CommitEvent} is fired.
	 * 
	 * @param event
	 */
	public void commit(CommitEvent event);

}
