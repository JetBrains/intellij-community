package com.siyeh.igtest.errorhandling.caught_exception_immediately_rethrown;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class CaughtExceptionImmediatelyRethrown {

    void foo() throws FileNotFoundException {
        try {
            new FileInputStream(new File(""));
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (FileNotFoundException <warning descr="Caught exception 'e' is immediately rethrown">e</warning>) {
            throw e;
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    void conflict() throws FileNotFoundException {
        try {
            int i = 0;
            new FileInputStream(new File(""));
        } catch (FileNotFoundException <warning descr="Caught exception 'e' is immediately rethrown">e</warning>) {
            throw e;
        }
        int i = 10;
    }

	void notImmediately(boolean notsure) throws InterruptedException {
		try {
			Thread.sleep(10000L);
		} catch (InterruptedException ex) {
			if (notsure) throw ex;
		}
	}

	protected static void getActionMethod(Class<?> actionClass, String methodName)
			throws IllegalArgumentException {
		try {
			System.out.println();
		} catch (IllegalArgumentException e) {
			// hmm -- OK, try something else instead
			try {
				System.out.println();
			} catch (IllegalArgumentException e1) {
				// throw the original one
				throw e;
			}
		}
	}

    public void test() throws IOException {
        try {
            // some code here
        } catch(IllegalStateException | UnsupportedOperationException e) {
            throw e;
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }
}