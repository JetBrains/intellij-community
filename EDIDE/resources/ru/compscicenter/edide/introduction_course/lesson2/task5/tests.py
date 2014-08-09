from test_helper import run_common_tests, passed, failed, import_task_file


def test_value():
    file = import_task_file()
    if file.number == 12.0:
        passed()
    else:
        failed("Use += operator")


if __name__ == '__main__':
    run_common_tests("You should modify the file")
    test_value()