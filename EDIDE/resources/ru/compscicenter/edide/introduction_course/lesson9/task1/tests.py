from test_helper import run_common_tests, get_task_windows, passed, failed


def test_window():
    window = get_task_windows()[0]
    if "import " in window:
        passed()
    else:
        failed("Use 'import' keyword")


def test_window1():
    window = get_task_windows()[0]
    if "my_module" in window:
        passed()
    else:
        failed("Import module my_module")


def test_window2():
    window = get_task_windows()[0]
    if "my_module.py" in window:
        failed("Don't use file extension here")
    else:
        passed()


def test_window3():
    window = get_task_windows()[1]
    if "my_module" in window and "hello_world" in window:
        passed()
    else:
        failed("Call hello_world function using my_module.hello_world")

if __name__ == '__main__':
    run_common_tests()
    test_window1()
    test_window()
    test_window2()
    test_window3()