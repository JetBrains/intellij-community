package com.intellij.openapi.application;

/**
 * Implementors of this interface declared via {@link com.intellij.ExtensionPoints.APPLICATION_STARTER} contribute a
 * command line application based on IDEA platform.
 * @author max
 */
public interface ApplicationStarter {
  /**
   * Command line switch to start with this runner. For example return "inspect" if you'd like to start app with
   * <code>idea.exe inspect</code> cmdline.
   * @return command line selector.
   */
  String getCommandName();

  /**
   * Called before application initialization. Invoked in awt dispatch thread.
   * @param args cmdline arguments including declared selector. For example <code>"idea.exe inspect myproject.ipr"</code>
   * will pass <code>{"inspect", "myproject.ipr"}</code>
   */
  void premain(String[] args);

  /**
   * Called when application have been initialized. Invoked in awt dispatch thread. An application starter should take care terminating
   * JVM itself when appropriate by calling {@link java.lang.System.exit(0);}
   * @param args cmdline arguments including declared selector. For example <code>"idea.exe inspect myproject.ipr"</code>
   * will pass <code>{"inspect", "myproject.ipr"}</code>
   */
  void main(String[] args);
}
