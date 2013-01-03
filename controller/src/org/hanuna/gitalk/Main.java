package org.hanuna.gitalk;

import org.hanuna.gitalk.controller.Controller;
import org.hanuna.gitalk.controller.git.log.readers.GitException;

import java.io.IOException;

/**
 * @author erokhins
 */
public class Main {

    public static void main(String[] args) throws IOException, GitException {
        Controller controller = new Controller();
        controller.run();
    }
}
