import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

class Foo {
  private void run(int port) throws Exception {
    Socket socket = new Socket("localhost", port);

    try {
      InputStream inputReader = socket.getInputStream();
      try {
        OutputStream outputWriter = socket.getOutputStream();
        try {
          while (true) {
            inputReader.read();
          }
        }
        finally {
          outputWriter.close();
        }
      }
      finally {
        inputReader.close();
      }
    }
    finally {
      socket.close();
    }
  }

}
