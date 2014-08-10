from test_helper import run_common_tests, import_task_file, passed, failed, get_task_windows


def test_value():
    file = import_task_file()
    if "dinosaur" in file.animals:
        passed()
    else:
        failed("Replace 'dino' with 'dinosaur'")

def test_window():
    window = get_task_windows()[0]
    if "animals" in window and "[" in window:
        passed()
    else:
        failed("Replace 'dino' with 'dinosaur'")

if __name__ == '__main__':
    run_common_tests("Use indexing and assignment")
    test_value()
    test_window()
