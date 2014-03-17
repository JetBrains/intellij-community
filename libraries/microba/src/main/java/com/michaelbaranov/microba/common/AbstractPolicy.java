package com.michaelbaranov.microba.common;

import javax.swing.event.EventListenerList;


/**
 * This is a convenience implementation of {@link Policy}.
 * 
 * @author Michael Baranov
 * 
 */
public abstract class AbstractPolicy implements Policy {
	private EventListenerList vetoPolicyListenerList = new EventListenerList();

	public void addVetoPolicyListener(PolicyListener listener) {
		vetoPolicyListenerList.add(PolicyListener.class, listener);
	}

	public void removeVetoPolicyListener(PolicyListener listener) {
		vetoPolicyListenerList.remove(PolicyListener.class, listener);

	}

	/**
	 * Fires a {@link PolicyEvent} to all related {@link PolicyListener}s.
	 */
	protected void fireVetoPolicyChangeAction() {
		Object[] listeners = vetoPolicyListenerList.getListenerList();

		for (int i = listeners.length - 2; i >= 0; i -= 2)
			if (listeners[i] == PolicyListener.class)
				((PolicyListener) listeners[i + 1])
						.policyChanged(new PolicyEvent(this));
	}

}
