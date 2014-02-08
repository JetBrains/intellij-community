import unittest
import sys
import os

class FirstTaskTestCase(unittest.TestCase):

    def task1_wrap(self):
        from StringIO import StringIO

        saved_stdout = sys.stdout
        try:
            out = StringIO()
            sys.stdout = out
            sys.path.append(os.getcwd())
            import helloworld
            output = out.getvalue().strip()
        finally:
            sys.stdout = saved_stdout
        return output

    def test_task(self):
        result_string = self.task1_wrap()
        self.assertEqual(result_string[:18], "Hello, world! I'm ")
        self.assertNotEqual(result_string[18:], "type your name")

if __name__ == '__main__':
    unittest.main()