import sys
def test(a, b):
    if a == b:
        print "OK"
    else:
        print "not OK"


def test_task():
    oldargv = sys.argv
    sys.argv = ["", "sum-input.txt"]
    (u, v) = s.get_max_sum()
    test((u,v), (1, 1))
    sys.argv = ["", "sum-input2.txt"]
    (u, v) = s.get_max_sum()
    test((u,v), (4, 4))
    sys.argv = ["", "sum-input3.txt"]
    (u, v) = s.get_max_sum()
    test((u,v), (4, 7))
    sys.argv = oldargv

if __name__ == '__main__':
    from sys import path
    import os.path
    parpath = os.path.abspath(os.path.join(os.getcwd(), os.pardir))
    task_path = os.path.join(parpath, "task3")
    path.append(task_path)
    s = __import__("sum")
    test_task()
