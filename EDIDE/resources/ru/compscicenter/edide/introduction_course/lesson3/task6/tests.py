from test_helper import run_common_tests, passed, failed, import_task_file, get_task_windows


def test_value():
    file = import_task_file()
    if file.contains:
        passed()
    else:
        failed("Use 'in' operator for this check")

def test_window():
    window = get_task_windows()[0]

    if " in " in window:
        passed()
    else:
        failed("Use 'in' operator for this check")


if __name__ == '__main__':
    run_common_tests()

    test_value()
    test_window()
