from test_helper import run_common_tests, import_file, failed, passed


def test_value(path):
    file = import_file(path)
    if file.python == "Python":
        passed()
    failed("Use slicing")


if __name__ == '__main__':
    run_common_tests('''monty_python = "Monty Python"
monty = monty_python[:5]
print(monty)
python = type here
print(python)
''', '''monty_python = "Monty Python"
monty = monty_python[:5]
print(monty)
python =
print(python)
''', "You should modify the file")

    import sys
    path = sys.argv[-1]
    test_value(path)

    #TODO: check that python extracted from monty_python