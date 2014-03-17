package com.michaelbaranov.microba.common;

import java.util.EventObject;

/**
 * An event used to indicate a change to a control has been commited or reverted
 * (rolled back).
 * 
 * @author Michael Baranov
 * 
 */
public class CommitEvent extends EventObject {

	private boolean commit;

	/**
	 * Constructor.
	 * 
	 * @param source
	 *            a control that fired the event
	 * @param commit
	 *            <code>true</code> to indicate commit, <code>false</code>
	 *            to indicate revert (rollback)
	 */
	public CommitEvent(Object source, boolean commit) {
		super(source);
		this.commit = commit;
	}

	/**
	 * Returns the type of the event.
	 * 
	 * @return <code>true</code> if a change has been commited to a control,
	 *         <code>false</code> otherwise.
	 */
	public boolean isCommit() {
		return commit;
	}

}
