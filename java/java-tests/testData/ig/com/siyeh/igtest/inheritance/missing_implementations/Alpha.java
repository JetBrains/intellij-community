package com.siyeh.igtest.inheritance.missing_implementations;

public abstract class Alpha {
	public abstract void execute(); // Reported not implemented in all subclasses.
}
