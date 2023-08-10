package com.siyeh.igtest.inheritance.missing_implementations;

public abstract class Bravo extends Alpha {
	@Override
	public final void execute() {
		perform();
	}

	protected abstract void perform();
}
