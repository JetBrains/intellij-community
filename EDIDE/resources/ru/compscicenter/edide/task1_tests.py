import sys
def task1_wrap():
        from StringIO import StringIO

        saved_stdout = sys.stdout
        try:
            out = StringIO()
            sys.stdout = out
            from sys import path
            import os.path
            parpath = os.path.abspath(os.path.join(os.getcwd(), os.pardir))
            task_path = os.path.join(parpath, "task1")
            path.append(task_path)
            import helloworld
            output = out.getvalue().strip()
        finally:
            sys.stdout = saved_stdout
        return output

def test_task():
        result_string = task1_wrap()
        if result_string[:18] != "Hello, world! I'm ":
            print "not OK"
            return
        print "OK"

if __name__ == '__main__':
    test_task()
