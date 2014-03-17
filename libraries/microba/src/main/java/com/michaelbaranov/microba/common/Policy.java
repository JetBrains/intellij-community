package com.michaelbaranov.microba.common;

public interface Policy {
	/**
	 * Adds a {@link PolicyListener}.
	 * 
	 * @param listener
	 *            a {@link PolicyListener} to add
	 */
	public void addVetoPolicyListener(PolicyListener listener);

	/**
	 * Removes a {@link PolicyListener}.
	 * 
	 * @param listener
	 *            a {@link PolicyListener} to remove
	 */
	public void removeVetoPolicyListener(PolicyListener listener);
}
